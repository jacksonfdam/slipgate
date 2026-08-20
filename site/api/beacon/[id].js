// The beacon: one small document that says where a player's own game library is right now.
//
// The problem it solves is that a home library moves. A quick tunnel gets a new hostname every time
// it starts, a NAS reboots after a power cut, and an app configured with a hostname is an app that
// has to be reconfigured on every device each time either happens. So the app is configured with
// this address instead — which never changes — and the NAS updates what it points at.
//
// Two callers, two directions:
//
//   POST  the NAS publishes a pointer, proving itself with the publish token.
//   GET   the app reads the pointer, proving itself by knowing the beacon id.
//
// The id is the read credential rather than a name, which is why it has to be long and random: the
// pointer holds the key to the library behind it, so whoever holds the id holds the library.
import { createHash, timingSafeEqual } from 'node:crypto';
import { del, list, put } from '@vercel/blob';

// Long enough that it cannot be guessed, and hex so it survives a URL, a QR code and being read
// aloud over a phone call.
const ID_PATTERN = /^[0-9a-f]{24,64}$/;

// A pointer is four short lines. A cap this low means a caller holding the token still cannot use
// the beacon as storage.
const MAX_POINTER_BYTES = 4096;

const POINTER_HEADER = 'slipgate-beacon 1';

export default async function handler(request, response) {
  cors(response);

  if (request.method === 'OPTIONS') {
    // The launcher's web build reads this from a browser, so the preflight is answered here.
    response.status(204).end();
    return;
  }

  const id = String(request.query.id ?? '').toLowerCase();
  if (!ID_PATTERN.test(id)) {
    response.status(400).type('text/plain').send('a beacon id is 24 to 64 hex characters\n');
    return;
  }

  const secret = process.env.SLIPGATE_BEACON_TOKEN;
  if (!secret) {
    response.status(503).type('text/plain').send('this beacon has no publish token configured\n');
    return;
  }

  switch (request.method) {
    case 'GET':
    case 'HEAD':
      await read(id, request, response);
      return;
    case 'POST':
    case 'PUT':
      await write(id, request, response, secret);
      return;
    case 'DELETE':
      await forget(id, request, response, secret);
      return;
    default:
      response.status(405).type('text/plain').send('GET to read, POST to publish\n');
  }
}

async function read(id, request, response) {
  const found = await locate(id);
  if (!found) {
    // 404 rather than an empty document: an app that gets a pointer it cannot use should hear that
    // the library has never announced itself, not that it is somewhere unreachable.
    response.status(404).type('text/plain').send('this beacon has nothing to point at\n');
    return;
  }

  // no-store on both hops. A pointer a CDN held for five minutes is a pointer that sends the app to
  // a tunnel which closed five minutes ago.
  const upstream = await fetch(found.url, { cache: 'no-store' });
  if (!upstream.ok) {
    response.status(502).type('text/plain').send('the pointer could not be read\n');
    return;
  }

  const pointer = await upstream.text();
  response.setHeader('cache-control', 'no-store, max-age=0');
  response.setHeader('x-slipgate-published', found.uploadedAt ?? '');
  response.status(200).type('text/plain; charset=utf-8');
  if (request.method === 'HEAD') {
    response.end();
    return;
  }
  response.send(pointer);
}

async function write(id, request, response, secret) {
  if (!authorised(request, secret)) {
    response.status(401).type('text/plain').send('the publish token is wrong or missing\n');
    return;
  }

  const pointer = await body(request);
  if (pointer.length > MAX_POINTER_BYTES) {
    response.status(413).type('text/plain').send('a pointer is a few lines, not a file\n');
    return;
  }

  const problem = invalid(pointer);
  if (problem) {
    // Validated rather than trusted, because everything downstream of this is an app that will try
    // to download game data from whatever address this document names.
    response.status(422).type('text/plain').send(`${problem}\n`);
    return;
  }

  await put(pathFor(id), pointer, {
    access: 'public',
    contentType: 'text/plain; charset=utf-8',
    addRandomSuffix: false,
    allowOverwrite: true,
    cacheControlMaxAge: 0,
  });
  response.status(204).end();
}

async function forget(id, request, response, secret) {
  if (!authorised(request, secret)) {
    response.status(401).type('text/plain').send('the publish token is wrong or missing\n');
    return;
  }
  const found = await locate(id);
  if (found) {
    await del(found.url);
  }
  response.status(204).end();
}

async function locate(id) {
  const path = pathFor(id);
  const { blobs } = await list({ prefix: path, limit: 1 });
  return blobs.find((blob) => blob.pathname === path) ?? null;
}

/**
 * Where one beacon's pointer is stored.
 *
 * The id is hashed together with the publish token rather than used as the path, so that knowing the
 * id — which every device the player configured knows — is not enough to construct the public blob
 * URL and skip this function. Reading a pointer then always goes through code that can refuse it.
 */
function pathFor(id) {
  const digest = createHash('sha256')
    .update(`${id}:${process.env.SLIPGATE_BEACON_TOKEN}`)
    .digest('hex');
  return `beacon/${digest}.txt`;
}

function authorised(request, secret) {
  const header = String(request.headers.authorization ?? '');
  if (!/^bearer /i.test(header)) {
    return false;
  }
  const offered = Buffer.from(header.slice('bearer '.length).trim());
  const expected = Buffer.from(secret);
  // Constant time, and only once the lengths match, because timingSafeEqual throws on a length
  // mismatch and the length is not the secret.
  return offered.length === expected.length && timingSafeEqual(offered, expected);
}

/** What is wrong with this pointer, or null when nothing is. */
function invalid(pointer) {
  const lines = pointer.split('\n').map((line) => line.trim()).filter(Boolean);
  if (lines[0] !== POINTER_HEADER) {
    return `a pointer starts with "${POINTER_HEADER}"`;
  }
  const fields = new Map(
    lines.slice(1).map((line) => {
      const [name, ...rest] = line.split('\t');
      return [name, rest.join('\t')];
    }),
  );
  const url = fields.get('url') ?? '';
  if (!url) {
    return 'a pointer needs a url line';
  }
  if (!url.startsWith('https://')) {
    // Plain HTTP would be a downgrade the player never asked for, and every tunnel worth using
    // terminates TLS anyway.
    return 'the url has to be https';
  }
  if (!fields.get('key')) {
    return 'a pointer needs a key line, or the app cannot read the library it points at';
  }
  return null;
}

/** The request body as text, whether the platform already parsed it or not. */
async function body(request) {
  if (typeof request.body === 'string') {
    return request.body;
  }
  if (Buffer.isBuffer(request.body)) {
    return request.body.toString('utf8');
  }
  if (request.body && typeof request.body === 'object') {
    // A caller that sent JSON is read as JSON and then fails validation below, which is a clearer
    // answer than a parse error.
    return JSON.stringify(request.body);
  }
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

function cors(response) {
  // Readable from anywhere, because the launcher's web build is a page on another origin and a
  // pointer is only useful to whoever already knows the id.
  response.setHeader('access-control-allow-origin', '*');
  response.setHeader('access-control-allow-methods', 'GET, HEAD, POST, DELETE, OPTIONS');
  response.setHeader('access-control-allow-headers', 'authorization, content-type');
  response.setHeader('access-control-max-age', '86400');
}

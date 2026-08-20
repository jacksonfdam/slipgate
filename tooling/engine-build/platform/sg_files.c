// A filesystem for the engine's own files, inside the module's memory.
//
// The module is built standalone, which means there is no filesystem behind its C library at all:
// fopen returns NULL, so every save the engines have ever written went nowhere. The host cannot
// supply one either — Emscripten's is written in JavaScript, and a gate runs on an interpreter.
//
// So the engines get this: a handful of files kept in linear memory, reached by wrapping the ten
// stdio calls their save code actually uses. Everything else about their I/O is untouched — the
// game data is mounted into memory by platform/sg_wad_file.c, and printing still goes to the host's
// log through stderr.
//
// Only paths under the save directory are ours. A path outside it falls through to the real call and
// fails exactly as it did before, which matters: the config writer uses fprintf, which is not
// wrapped, and handing it one of these files would write through a pointer it must never follow.

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>

#include "doomtype.h"

#include "sg_platform.h"

#define SAVE_ROOT "/slipgate/"

// Only the savegame directory is ours, not the whole preferences folder.
//
// The engines write their config there too, with fprintf, and read it back with fscanf. Neither is
// wrapped — a variadic call cannot be served from here without reimplementing a printf — so handing
// one of these files to the config writer would have it write through a pointer it must never
// follow. Every engine derives this directory from M_GetSaveGameDir, so one prefix covers all three.
#define SAVE_AREA "/slipgate/savegames"

// Hexen writes the hub plus a file per visited map, across six slots. Sixty-four covers that.
#define MAX_FILES 64
#define MAX_PATH_BYTES 128
// A Doom save of a large level runs to a few hundred kilobytes; a megabyte is room to be wrong in.
#define GROWTH_BYTES (64 * 1024)
#define MAX_FILE_BYTES (1024 * 1024)

#define MAX_STREAMS 8
// Handed out where a FILE * is expected. Tagged rather than a real pointer so that anything which
// dereferences it traps here and now instead of corrupting memory quietly.
#define STREAM_TAG 0x51F00000

typedef struct
{
    boolean used;
    char path[MAX_PATH_BYTES];
    unsigned char *data;
    long size;
    long capacity;
} sg_file_t;

typedef struct
{
    boolean open;
    int file;
    long position;
    boolean writing;
    boolean past_end;
} sg_stream_t;

static sg_file_t files[MAX_FILES];
static sg_stream_t streams[MAX_STREAMS];

#if defined(__wasm__)
// The wasm link reroutes the engine's calls here with --wrap, so the real calls keep their
// __real_ names.
extern FILE *__real_fopen(const char *path, const char *mode);
extern int __real_fclose(FILE *stream);
extern size_t __real_fread(void *buffer, size_t size, size_t count, FILE *stream);
extern size_t __real_fwrite(const void *buffer, size_t size, size_t count, FILE *stream);
extern int __real_fseek(FILE *stream, long offset, int whence);
extern long __real_ftell(FILE *stream);
extern int __real_feof(FILE *stream);
extern int __real_fgetc(FILE *stream);
extern int __real_remove(const char *path);
extern int __real_rename(const char *from, const char *to);
extern int __real_mkdir(const char *path, mode_t mode);
#else
// The native build reroutes by macro instead — Mach-O's linker has no --wrap — so every other
// compilation unit is force-fed a header that renames the ten calls, this file is compiled
// without it, and the real calls are the C library's own.
#define __real_fopen fopen
#define __real_fclose fclose
#define __real_fread fread
#define __real_fwrite fwrite
#define __real_fseek fseek
#define __real_ftell ftell
#define __real_feof feof
#define __real_fgetc fgetc
#define __real_remove remove
#define __real_rename rename
#define __real_mkdir mkdir
#endif

static boolean ours(const char *path)
{
    return path != NULL && strncmp(path, SAVE_AREA, strlen(SAVE_AREA)) == 0;
}

static int stream_index(FILE *stream)
{
    uintptr_t handle = (uintptr_t)stream;
    // Compared at full pointer width: on a 64-bit target a real FILE * has high bits set, and
    // matching only the low word could mistake one for a tagged handle.
    if ((handle & ~(uintptr_t)0xFFFFFu) != (uintptr_t)STREAM_TAG)
    {
        return -1;
    }
    int index = (int)(handle & 0xFFFFFu);
    return (index >= 0 && index < MAX_STREAMS && streams[index].open) ? index : -1;
}

static FILE *stream_handle(int index)
{
    return (FILE *)(uintptr_t)(STREAM_TAG | (uintptr_t)index);
}

static int find_file(const char *path)
{
    for (int index = 0; index < MAX_FILES; index++)
    {
        if (files[index].used && strcmp(files[index].path, path) == 0)
        {
            return index;
        }
    }
    return -1;
}

static int make_file(const char *path)
{
    for (int index = 0; index < MAX_FILES; index++)
    {
        if (files[index].used)
        {
            continue;
        }
        files[index].used = true;
        strncpy(files[index].path, path, MAX_PATH_BYTES - 1);
        files[index].path[MAX_PATH_BYTES - 1] = '\0';
        files[index].data = NULL;
        files[index].size = 0;
        files[index].capacity = 0;
        return index;
    }
    return -1;
}

static boolean reserve(int index, long wanted)
{
    if (wanted <= files[index].capacity)
    {
        return true;
    }
    if (wanted > MAX_FILE_BYTES)
    {
        return false;
    }
    long capacity = files[index].capacity == 0 ? GROWTH_BYTES : files[index].capacity;
    while (capacity < wanted)
    {
        capacity *= 2;
    }
    unsigned char *grown = (unsigned char *)realloc(files[index].data, (size_t)capacity);
    if (grown == NULL)
    {
        return false;
    }
    files[index].data = grown;
    files[index].capacity = capacity;
    return true;
}

FILE *__wrap_fopen(const char *path, const char *mode)
{
    if (!ours(path))
    {
        return __real_fopen(path, mode);
    }

    boolean writing = strchr(mode, 'w') != NULL || strchr(mode, 'a') != NULL;
    int file = find_file(path);
    if (file < 0)
    {
        if (!writing)
        {
            return NULL;
        }
        file = make_file(path);
        if (file < 0)
        {
            return NULL;
        }
    }
    if (writing && strchr(mode, 'a') == NULL)
    {
        files[file].size = 0;
    }

    for (int index = 0; index < MAX_STREAMS; index++)
    {
        if (streams[index].open)
        {
            continue;
        }
        streams[index].open = true;
        streams[index].file = file;
        streams[index].writing = writing;
        streams[index].past_end = false;
        streams[index].position = strchr(mode, 'a') != NULL ? files[file].size : 0;
        return stream_handle(index);
    }
    return NULL;
}

int __wrap_fclose(FILE *stream)
{
    int index = stream_index(stream);
    if (index < 0)
    {
        return __real_fclose(stream);
    }
    streams[index].open = false;
    return 0;
}

size_t __wrap_fread(void *buffer, size_t size, size_t count, FILE *stream)
{
    int index = stream_index(stream);
    if (index < 0)
    {
        return __real_fread(buffer, size, count, stream);
    }
    if (size == 0 || count == 0)
    {
        return 0;
    }

    sg_file_t *file = &files[streams[index].file];
    long remaining = file->size - streams[index].position;
    if (remaining <= 0)
    {
        streams[index].past_end = true;
        return 0;
    }
    size_t whole = (size_t)remaining / size;
    size_t taken = whole < count ? whole : count;
    memcpy(buffer, file->data + streams[index].position, taken * size);
    streams[index].position += (long)(taken * size);
    if (taken < count)
    {
        streams[index].past_end = true;
    }
    return taken;
}

size_t __wrap_fwrite(const void *buffer, size_t size, size_t count, FILE *stream)
{
    int index = stream_index(stream);
    if (index < 0)
    {
        return __real_fwrite(buffer, size, count, stream);
    }
    if (size == 0 || count == 0)
    {
        return 0;
    }

    sg_file_t *file = &files[streams[index].file];
    long end = streams[index].position + (long)(size * count);
    if (!reserve(streams[index].file, end))
    {
        return 0;
    }
    // A seek past the end followed by a write leaves a hole, which reads as zeroes.
    if (streams[index].position > file->size)
    {
        memset(file->data + file->size, 0, (size_t)(streams[index].position - file->size));
    }
    memcpy(file->data + streams[index].position, buffer, size * count);
    streams[index].position = end;
    if (end > file->size)
    {
        file->size = end;
    }
    return count;
}

int __wrap_fseek(FILE *stream, long offset, int whence)
{
    int index = stream_index(stream);
    if (index < 0)
    {
        return __real_fseek(stream, offset, whence);
    }

    long base = 0;
    switch (whence)
    {
        case SEEK_SET:
            base = 0;
            break;
        case SEEK_CUR:
            base = streams[index].position;
            break;
        case SEEK_END:
            base = files[streams[index].file].size;
            break;
        default:
            return -1;
    }
    if (base + offset < 0)
    {
        return -1;
    }
    streams[index].position = base + offset;
    streams[index].past_end = false;
    return 0;
}

long __wrap_ftell(FILE *stream)
{
    int index = stream_index(stream);
    return index < 0 ? __real_ftell(stream) : streams[index].position;
}

int __wrap_feof(FILE *stream)
{
    int index = stream_index(stream);
    return index < 0 ? __real_feof(stream) : (streams[index].past_end ? 1 : 0);
}

int __wrap_fgetc(FILE *stream)
{
    int index = stream_index(stream);
    if (index < 0)
    {
        return __real_fgetc(stream);
    }
    unsigned char byte = 0;
    return __wrap_fread(&byte, 1, 1, stream) == 1 ? (int)byte : EOF;
}

int __wrap_remove(const char *path)
{
    if (!ours(path))
    {
        return __real_remove(path);
    }
    int file = find_file(path);
    if (file < 0)
    {
        return -1;
    }
    free(files[file].data);
    files[file].used = false;
    files[file].data = NULL;
    files[file].size = 0;
    files[file].capacity = 0;
    return 0;
}

int __wrap_rename(const char *from, const char *to)
{
    if (!ours(from) || !ours(to))
    {
        return __real_rename(from, to);
    }
    int file = find_file(from);
    if (file < 0)
    {
        return -1;
    }
    int existing = find_file(to);
    if (existing >= 0)
    {
        __wrap_remove(to);
    }
    strncpy(files[file].path, to, MAX_PATH_BYTES - 1);
    files[file].path[MAX_PATH_BYTES - 1] = '\0';
    return 0;
}

// Directories are not a thing here: a path is a name. Reporting success is what stops the engine
// deciding it cannot save before it tries.
int __wrap_mkdir(const char *path, mode_t mode)
{
    return ours(path) ? 0 : __real_mkdir(path, mode);
}

// What the host reads and writes. The table is the filesystem, so these need no directory walk.
int sg_file_count(void)
{
    int count = 0;
    for (int index = 0; index < MAX_FILES; index++)
    {
        if (files[index].used)
        {
            count++;
        }
    }
    return count;
}

static int nth_used(int wanted)
{
    int seen = 0;
    for (int index = 0; index < MAX_FILES; index++)
    {
        if (!files[index].used)
        {
            continue;
        }
        if (seen == wanted)
        {
            return index;
        }
        seen++;
    }
    return -1;
}

const char *sg_file_name(int index)
{
    int file = nth_used(index);
    return file < 0 ? NULL : files[file].path + strlen(SAVE_ROOT);
}

const char *sg_file_path(int index)
{
    int file = nth_used(index);
    return file < 0 ? NULL : files[file].path;
}

boolean sg_path_is_ours(const char *path)
{
    return ours(path);
}

long sg_file_size(int index)
{
    int file = nth_used(index);
    return file < 0 ? -1 : files[file].size;
}

long sg_file_read(int index, unsigned char *destination, long capacity)
{
    int file = nth_used(index);
    if (file < 0 || files[file].size > capacity)
    {
        return -1;
    }
    memcpy(destination, files[file].data, (size_t)files[file].size);
    return files[file].size;
}

boolean sg_file_write(const char *name, const unsigned char *data, long size)
{
    char path[MAX_PATH_BYTES];
    if ((size_t)snprintf(path, sizeof(path), "%s%s", SAVE_ROOT, name) >= sizeof(path))
    {
        return false;
    }

    int file = find_file(path);
    if (file < 0)
    {
        file = make_file(path);
    }
    if (file < 0 || !reserve(file, size))
    {
        return false;
    }
    memcpy(files[file].data, data, (size_t)size);
    files[file].size = size;
    return true;
}

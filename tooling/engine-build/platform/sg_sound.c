// Sound effects mixed on demand, in the amount the elapsed time has earned.
//
// Music is deliberately absent: the specification builds it later, behind a measured performance
// budget, because OPL emulation on an interpreter is a different question from mixing a few
// sound effects. Everything here is the sound effect path only, and the music entry points are
// honest no-ops rather than silent failures.
//
// Mixing happens when the host drains rather than on a timer, so what the host hears is a function
// of the frames it asked for and nothing else. That is the same reason the clock is driven by the
// host: an engine that mixes on its own schedule cannot be replayed.

#include <stdlib.h>
#include <string.h>

#include "config.h"
#include "deh_str.h"
#include "doomtype.h"
#include "i_sound.h"
#include "m_misc.h"
#include "w_wad.h"
#include "z_zone.h"

#include "sg_platform.h"

#define MIX_CHANNELS 16
#define MILLIS_PER_SECOND 1000
// Half a second. A host that stops draining must not bank minutes of audio it would then rush.
#define MAX_BUDGET_MILLIS 500

#define DMX_HEADER_BYTES 8
#define DMX_SKIPPED_BYTES 16
#define DMX_SHORTEST_SOUND 48
#define PANNING_MAX 255
#define VOLUME_MAX 127
#define SAMPLE_MIN (-32768)
#define SAMPLE_MAX 32767
#define UNSIGNED_TO_SIGNED 128
#define BYTE_TO_SAMPLE_SHIFT 8

typedef struct
{
    int length;
    int16_t *samples;
} sg_sound_t;

typedef struct
{
    sg_sound_t *sound;
    int position;
    int left;
    int right;
    boolean active;
} sg_channel_t;

static sg_channel_t channels[MIX_CHANNELS];
static int sfx_volume = VOLUME_MAX;
static int budget_frames;
static int budget_remainder;

int snd_samplerate = 44100;
int snd_sfxdevice = SNDDEVICE_SB;
int snd_musicdevice = SNDDEVICE_NONE;
int snd_maxslicetime_ms = 28;
char *snd_musiccmd = "";
int snd_pitchshift = 0;

// Whether this game's sound lumps carry the "ds" prefix. Doom and Strife name them dsposit and
// dspistol; Raven's engines name theirs plainly, and asking for dsdorcls in Heretic is a lump that
// does not exist. Upstream makes the same decision from the same value in i_sdlsound.c.
static boolean sfx_prefixed = true;

void I_InitSound(GameMission_t mission)
{
    sfx_prefixed = (mission == doom || mission == strife);
    sg_audio_reset();
}

void I_ShutdownSound(void)
{
    sg_audio_reset();
}

// Converts a DMX sound to the output rate once and keeps it. The engine asks for the same sounds
// over and over, and resampling on every shot would be the most expensive thing in the mixer.
static sg_sound_t *cache_sound(sfxinfo_t *sfxinfo)
{
    if (sfxinfo->driver_data != NULL)
    {
        return (sg_sound_t *)sfxinfo->driver_data;
    }

    unsigned int lump_length = W_LumpLength(sfxinfo->lumpnum);
    const byte *data = W_CacheLumpNum(sfxinfo->lumpnum, PU_STATIC);

    if (lump_length < DMX_HEADER_BYTES || data[0] != 0x03 || data[1] != 0x00)
    {
        W_ReleaseLumpNum(sfxinfo->lumpnum);
        return NULL;
    }

    int source_rate = (data[3] << 8) | data[2];
    unsigned int length =
        (data[7] << 24) | (data[6] << 16) | (data[5] << 8) | data[4];

    // DMX discards very short sounds and skips sixteen bytes at each end of the samples; both are
    // quirks of the original library that the sounds themselves were authored against.
    if (source_rate <= 0 || length > lump_length - DMX_HEADER_BYTES
        || length <= DMX_SHORTEST_SOUND + 2 * DMX_SKIPPED_BYTES)
    {
        W_ReleaseLumpNum(sfxinfo->lumpnum);
        return NULL;
    }

    const byte *samples = data + DMX_HEADER_BYTES + DMX_SKIPPED_BYTES;
    int source_length = (int)length - 2 * DMX_SKIPPED_BYTES;
    int output_length =
        (int)(((int64_t)source_length * snd_samplerate) / source_rate);

    sg_sound_t *sound = malloc(sizeof(sg_sound_t));
    int16_t *converted = malloc((size_t)output_length * sizeof(int16_t));

    if (sound == NULL || converted == NULL)
    {
        free(sound);
        free(converted);
        W_ReleaseLumpNum(sfxinfo->lumpnum);
        return NULL;
    }

    // Linear interpolation, because nearest sampling at 11 kHz to 44 kHz is audibly stepped on the
    // long sounds and this costs one multiply per output sample.
    for (int index = 0; index < output_length; index++)
    {
        int64_t position = (int64_t)index * source_rate;
        int source_index = (int)(position / snd_samplerate);
        int fraction = (int)(position % snd_samplerate);
        int first = samples[source_index] - UNSIGNED_TO_SIGNED;
        int second =
            source_index + 1 < source_length
                ? samples[source_index + 1] - UNSIGNED_TO_SIGNED
                : first;
        int interpolated =
            first + (int)(((int64_t)(second - first) * fraction) / snd_samplerate);
        converted[index] = (int16_t)(interpolated << BYTE_TO_SAMPLE_SHIFT);
    }

    W_ReleaseLumpNum(sfxinfo->lumpnum);

    sound->length = output_length;
    sound->samples = converted;
    sfxinfo->driver_data = sound;
    return sound;
}

int I_GetSfxLumpNum(sfxinfo_t *sfxinfo)
{
    char name[9];

    // A linked sound is not a lump of its own: Heretic's table names one "-impact" and points it at
    // the imp's own sit sound, and the leading dash says so. Asking the wad for "-impact" is a fatal
    // error, which is what killed the gate a few seconds into its own attract loop.
    if (sfxinfo->link != NULL)
    {
        sfxinfo = sfxinfo->link;
    }

    // Dehacked can rename a sound, and every other lookup in the engine goes through DEH_String.
    if (sfx_prefixed)
    {
        M_snprintf(name, sizeof(name), "ds%s", DEH_String(sfxinfo->name));
    }
    else
    {
        M_StringCopy(name, DEH_String(sfxinfo->name), sizeof(name));
    }
    return W_GetNumForName(name);
}

// Nothing to do: the host pulls, so there is no queue to top up between frames.
void I_UpdateSound(void)
{
}

// The panning arithmetic is the original's, so a sound sits where the engine expects it to.
void I_UpdateSoundParams(int channel, int vol, int sep)
{
    if (channel < 0 || channel >= MIX_CHANNELS)
    {
        return;
    }

    int left = ((254 - sep) * vol) / VOLUME_MAX;
    int right = (sep * vol) / VOLUME_MAX;

    channels[channel].left = left < 0 ? 0 : (left > PANNING_MAX ? PANNING_MAX : left);
    channels[channel].right = right < 0 ? 0 : (right > PANNING_MAX ? PANNING_MAX : right);
}

int I_StartSound(sfxinfo_t *sfxinfo, int channel, int vol, int sep, int pitch)
{
    // Pitch shifting is a configuration option the specification leaves off, and honouring it
    // would mean a second cached copy of every sound per pitch.
    (void)pitch;

    if (channel < 0 || channel >= MIX_CHANNELS)
    {
        return channel;
    }

    sg_sound_t *sound = cache_sound(sfxinfo);

    if (sound == NULL)
    {
        channels[channel].active = false;
        return channel;
    }

    channels[channel].sound = sound;
    channels[channel].position = 0;
    channels[channel].active = true;
    I_UpdateSoundParams(channel, vol, sep);
    return channel;
}

void I_StopSound(int channel)
{
    if (channel >= 0 && channel < MIX_CHANNELS)
    {
        channels[channel].active = false;
    }
}

boolean I_SoundIsPlaying(int channel)
{
    if (channel < 0 || channel >= MIX_CHANNELS)
    {
        return false;
    }

    return channels[channel].active;
}

void I_PrecacheSounds(sfxinfo_t *sounds, int num_sounds)
{
    for (int index = 0; index < num_sounds; index++)
    {
        cache_sound(&sounds[index]);
    }
}

void I_InitMusic(void)
{
}

void I_ShutdownMusic(void)
{
}

void I_SetMusicVolume(int volume)
{
    (void)volume;
}

void I_PauseSong(void)
{
}

void I_ResumeSong(void)
{
}

void *I_RegisterSong(void *data, int len)
{
    (void)data;
    (void)len;
    return NULL;
}

void I_UnRegisterSong(void *handle)
{
    (void)handle;
}

void I_PlaySong(void *handle, boolean looping)
{
    (void)handle;
    (void)looping;
}

void I_StopSong(void)
{
}

boolean I_MusicIsPlaying(void)
{
    return false;
}

void I_BindSoundVariables(void)
{
}

void I_SetSfxVolume(int volume)
{
    sfx_volume = volume < 0 ? 0 : (volume > VOLUME_MAX ? VOLUME_MAX : volume);
}

void I_SetOPLDriverVer(opl_driver_ver_t ver)
{
    (void)ver;
}

// The OPL music code prints its own diagnostics; without it, nothing has messages to report.
void I_OPL_DevMessages(char *result, size_t result_len)
{
    if (result_len > 0)
    {
        result[0] = '\0';
    }
}

void sg_audio_reset(void)
{
    memset(channels, 0, sizeof(channels));
    budget_frames = 0;
    budget_remainder = 0;
}

// Earns the frames that [elapsed_millis] of play is worth. The remainder is carried so that a rate
// which does not divide the frame length evenly still adds up exactly over time.
void sg_audio_advance(int elapsed_millis)
{
    if (elapsed_millis <= 0)
    {
        return;
    }

    int scaled = elapsed_millis * snd_samplerate + budget_remainder;
    budget_frames += scaled / MILLIS_PER_SECOND;
    budget_remainder = scaled % MILLIS_PER_SECOND;

    int ceiling = snd_samplerate * MAX_BUDGET_MILLIS / MILLIS_PER_SECOND;
    if (budget_frames > ceiling)
    {
        budget_frames = ceiling;
    }
}

static int mix_channel(sg_channel_t *channel, int16_t *destination, int frames)
{
    sg_sound_t *sound = channel->sound;
    int mixed = 0;

    while (mixed < frames && channel->position < sound->length)
    {
        int sample = sound->samples[channel->position];
        int left = destination[mixed * 2]
                   + (sample * channel->left * sfx_volume) / (PANNING_MAX * VOLUME_MAX);
        int right = destination[mixed * 2 + 1]
                    + (sample * channel->right * sfx_volume) / (PANNING_MAX * VOLUME_MAX);

        destination[mixed * 2] =
            (int16_t)(left < SAMPLE_MIN ? SAMPLE_MIN : (left > SAMPLE_MAX ? SAMPLE_MAX : left));
        destination[mixed * 2 + 1] =
            (int16_t)(right < SAMPLE_MIN ? SAMPLE_MIN : (right > SAMPLE_MAX ? SAMPLE_MAX : right));

        channel->position++;
        mixed++;
    }

    if (channel->position >= sound->length)
    {
        channel->active = false;
    }

    return mixed;
}

// Mixes what the elapsed time has earned, in frames of two interleaved samples. Returning fewer
// frames than asked for is normal: it means the engine has not played that much yet.
int sg_audio_drain(int16_t *destination, int frames)
{
    if (frames > budget_frames)
    {
        frames = budget_frames;
    }
    if (frames <= 0)
    {
        return 0;
    }

    memset(destination, 0, (size_t)frames * 2 * sizeof(int16_t));

    for (int channel = 0; channel < MIX_CHANNELS; channel++)
    {
        if (channels[channel].active)
        {
            mix_channel(&channels[channel], destination, frames);
        }
    }

    budget_frames -= frames;
    return frames;
}

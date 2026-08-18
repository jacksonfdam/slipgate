// Sound rendered into a ring buffer the host drains.
//
// Music is deliberately absent: the specification builds it later, behind a measured performance
// budget, because OPL emulation on an interpreter is a different question from mixing a few
// sound effects. Everything here is the sound effect path only, and the music entry points are
// honest no-ops rather than silent failures.

#include <stdlib.h>
#include <string.h>

#include "config.h"
#include "doomtype.h"
#include "i_sound.h"
#include "m_misc.h"
#include "w_wad.h"

#include "sg_platform.h"

#define RING_FRAMES 8192
#define RING_SAMPLES (RING_FRAMES * 2)

int snd_samplerate = 44100;
int snd_sfxdevice = SNDDEVICE_SB;
int snd_musicdevice = SNDDEVICE_NONE;
int snd_maxslicetime_ms = 28;
char *snd_musiccmd = "";
int snd_pitchshift = 0;

static int16_t ring[RING_SAMPLES];
static int ring_write = 0;
static int ring_read = 0;

void I_InitSound(GameMission_t mission)
{
    (void)mission;
    sg_audio_reset();
}

void I_ShutdownSound(void)
{
    sg_audio_reset();
}

int I_GetSfxLumpNum(sfxinfo_t *sfxinfo)
{
    char name[9];

    M_snprintf(name, sizeof(name), "ds%s", sfxinfo->name);
    return W_GetNumForName(name);
}

void I_UpdateSound(void)
{
}

void I_UpdateSoundParams(int channel, int vol, int sep)
{
    (void)channel;
    (void)vol;
    (void)sep;
}

int I_StartSound(sfxinfo_t *sfxinfo, int channel, int vol, int sep, int pitch)
{
    (void)sfxinfo;
    (void)vol;
    (void)sep;
    (void)pitch;
    return channel;
}

void I_StopSound(int channel)
{
    (void)channel;
}

boolean I_SoundIsPlaying(int channel)
{
    (void)channel;
    return false;
}

void I_PrecacheSounds(sfxinfo_t *sounds, int num_sounds)
{
    (void)sounds;
    (void)num_sounds;
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
    (void)volume;
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
    memset(ring, 0, sizeof(ring));
    ring_write = 0;
    ring_read = 0;
}

// Hands the host whatever has been mixed, in frames of two interleaved samples. Returning fewer
// frames than asked for is normal: it means the engine has not produced that much audio yet.
int sg_audio_drain(int16_t *destination, int frames)
{
    int written = 0;

    while (written < frames && ring_read != ring_write)
    {
        destination[written * 2] = ring[ring_read];
        destination[written * 2 + 1] = ring[ring_read + 1];
        ring_read = (ring_read + 2) % RING_SAMPLES;
        written++;
    }

    return written;
}

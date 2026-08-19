// Time, as told by the host.
//
// The engine asks what time it is and expects tics at 35 Hz. Slipgate advances a clock the host
// drives instead of reading a system timer, so a headless run stepping fixed intervals produces
// exactly the same tics as a real one — which is what makes demo playback comparable at all.

#include "config.h"
#include "doomtype.h"
#include "i_timer.h"

#include "sg_platform.h"

#define TICRATE_HZ 35
#define MILLIS_PER_SECOND 1000

static int elapsed_millis = 0;

void I_InitTimer(void)
{
    elapsed_millis = 0;
}

int I_GetTime(void)
{
    return (elapsed_millis * TICRATE_HZ) / MILLIS_PER_SECOND;
}

int I_GetTimeMS(void)
{
    return elapsed_millis;
}

// The engine sleeps in a loop while it waits for the next tic — during start-up, before the host
// has stepped it even once. With a clock only the host advances, that loop never ends, so sleeping
// is what moves time here. It stays deterministic: the same sequence of sleeps advances the clock
// by the same amount every run.
void I_Sleep(int ms)
{
    if (ms > 0)
    {
        elapsed_millis += ms;
    }
}

void I_WaitVBL(int count)
{
    (void)count;
}

void sg_set_elapsed_millis(int elapsed)
{
    elapsed_millis = elapsed;
}

int sg_elapsed_millis(void)
{
    return elapsed_millis;
}

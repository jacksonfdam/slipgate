// What one frame of Hexen is, and how a demo starts.
//
// Hexen is the engine whose frame cannot simply be called. Doom has D_RunFrame; Heretic's loop body
// is four public calls, so the platform layer can make them itself. Hexen's is five, and the last of
// them — DrawAndBlit — is static to h2_main.c, along with the page drawer and the message drawer it
// calls. Copying it here would mean copying its file-local state too, and a copy that drifts from the
// engine is worse than no copy.
//
// So this runs the engine's own loop for exactly one iteration. H2_GameLoop is entered per step, and
// the frame boundary at the top of its second iteration is where the layer leaves it again, by the
// same longjmp mechanism that escapes start-up. What runs in between is the engine's real loop body,
// statics and all.

#include <setjmp.h>

#include "h2def.h"

#include "sg_platform.h"

extern void H2_GameLoop(void);

static jmp_buf frame_escape;

// Whether the loop is being run for a step, and how many of its boundaries have been seen. Zero is
// the boundary that starts the frame; the next one is where the frame ended.
static boolean stepping = false;
static int boundaries = 0;

void sg_engine_run_frame(void)
{
    if (setjmp(frame_escape) == 0)
    {
        stepping = true;
        boundaries = 0;
        // Never returns: the loop is infinite and the boundary below is the way out of it.
        H2_GameLoop();
    }
    stepping = false;
}

void sg_engine_frame_boundary(void)
{
    if (!stepping)
    {
        return;
    }
    if (boundaries > 0)
    {
        longjmp(frame_escape, 1);
    }
    boundaries++;
}

boolean sg_engine_play_demo(const char *name, boolean single)
{
    singledemo = single;
    G_DeferedPlayDemo(name);
    return true;
}

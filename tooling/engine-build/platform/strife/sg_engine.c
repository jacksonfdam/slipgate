// What one frame of Strife is, and how a demo starts.
//
// Strife has no D_RunFrame, the same as Heretic and Hexen: only Doom's d_main.c was factored that
// way upstream, and Strife's D_DoomLoop is still a while loop around four calls. Rather than patch
// the engine, the frame is those four calls, made from here.
//
// The one difference from Heretic's is screenvisible, which the engine's own loop guards D_Display
// with. It is what the game sets while the intro is on screen, and honouring it here keeps the host
// from drawing a frame the engine considers not to exist.
//
// Everything D_DoomLoop does before its loop — the window icon, I_InitGraphics, the grab callback —
// has already run by the time the host escapes start-up, because the escape happens inside the
// loop's first I_StartFrame. That path needs showintro false, which is what the gate's -nograph
// argument is for: with the intro on, the engine never reaches I_InitGraphics at all.

#include "doomdef.h"
#include "doomtype.h"

#include "d_loop.h"
#include "doomstat.h"
#include "g_game.h"
#include "i_video.h"
#include "s_sound.h"

#include "sg_platform.h"

extern void D_Display(void);

void sg_engine_run_frame(void)
{
    // Frame-synchronous input, then at least one tic, then the sounds that moved with it, then the
    // picture: the order is the engine's own, and it matters — a tic run after the draw would show
    // the player the frame before the one they just asked for.
    I_StartFrame();
    TryRunTics();
    S_UpdateSounds(players[consoleplayer].mo);

    if (screenvisible)
    {
        D_Display();
    }
}

boolean sg_engine_play_demo(const char *name, boolean single)
{
    singledemo = single;
    G_DeferedPlayDemo(name);
    return true;
}

// Strife's frame is a call, so there is nothing to escape from and no boundary to watch.
void sg_engine_frame_boundary(void)
{
}

// What one frame of Heretic is, and how a demo starts.
//
// Heretic has no D_RunFrame: only Doom's d_main.c was factored that way upstream, and Heretic's
// D_DoomLoop is still a while loop around four calls. Rather than patch the engine, the frame is
// those four calls, made from here — the same inversion the Doom gate gets for free.
//
// Everything D_DoomLoop does before its loop — the graphics check, the mouse callback, the window
// icon, I_InitGraphics — has already run by the time the host escapes start-up, because the escape
// happens inside the loop's first I_StartFrame.

#include "doomdef.h"
#include "doomtype.h"

#include "d_loop.h"
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
    D_Display();
}

boolean sg_engine_play_demo(const char *name, boolean single)
{
    singledemo = single;
    G_DeferedPlayDemo(name);
    return true;
}

// Heretic's frame is a call, so there is nothing to escape from and no boundary to watch.
void sg_engine_frame_boundary(void)
{
}

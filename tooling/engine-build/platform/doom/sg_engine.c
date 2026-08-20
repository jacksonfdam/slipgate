// What one frame of Doom is, and how a demo starts.
//
// Everything else in platform/ is engine-agnostic: it talks to the shared i_* interface that every
// game in the tree uses. These two calls are not, because each game keeps its own main loop and its
// own demo entry point, and the exported surface must not know which game is behind it.
//
// Doom's own frame function is D_RunFrame, which this revision already factored out of D_DoomLoop.

#include "doomtype.h"

// Doom's start-up and demo entry points: the platform layer calls them rather than reimplementing
// what the game already knows how to do.
#include "doomstat.h"
#include "g_game.h"

#include "sg_platform.h"

extern void D_RunFrame(void);

void sg_engine_run_frame(void)
{
    D_RunFrame();
}

boolean sg_engine_play_demo(const char *name, boolean single)
{
    singledemo = single;
    G_DeferedPlayDemo(name);
    return true;
}

// Doom's frame is a call, so there is nothing to escape from and no boundary to watch.
void sg_engine_frame_boundary(void)
{
}

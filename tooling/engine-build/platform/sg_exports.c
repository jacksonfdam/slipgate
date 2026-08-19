// The surface the host calls.
//
// Every gate exposes the same handful of functions regardless of which engine is behind them, so
// host/runtime sees one shape and the launcher never learns which game it is running.
//
// The interesting part is the boot: D_DoomMain never returns, because it ends in the main loop's
// infinite loop. Rather than patch the engine, the platform layer sets a jump before calling it
// and escapes from the first I_StartFrame — by which point every initialisation the loop does has
// already run. Stepping afterwards is one frame of whichever engine was linked in, which is what
// sg_engine_run_frame is for.

#include <setjmp.h>
#include <stdlib.h>
#include <string.h>

#include "config.h"
#include "doomtype.h"
#include "i_system.h"
#include "i_video.h"
#include "m_argv.h"
#include "m_misc.h"
#include "w_wad.h"

#include "sg_platform.h"

extern void D_DoomMain(void);

#define SNAPSHOT_BYTES 8

static jmp_buf boot_escape;
// Quitting is an outcome, not a failure: the engine never returns from the frame it quits in, so the
// only way to report it as one is to leave that frame the same way boot leaves D_DoomMain.
static jmp_buf step_escape;
static boolean step_escape_valid = false;
static boolean booting = false;
static boolean booted = false;
static boolean finished = false;
static boolean frame_complete = false;
static int last_palette_generation = -1;

void sg_mark_frame_complete(void)
{
    frame_complete = true;
}

boolean sg_take_frame_complete(void)
{
    boolean complete = frame_complete;
    frame_complete = false;
    return complete;
}

// Called by the engine when the player, a demo or a script asks it to stop. Reported to the host as
// a finished session rather than as an error, because that is what it is.
void sg_request_quit(void)
{
    finished = true;
    if (step_escape_valid)
    {
        step_escape_valid = false;
        longjmp(step_escape, 1);
    }
    if (booting)
    {
        longjmp(boot_escape, 1);
    }
}

// Called at the top of every frame by the engine. During boot it is the escape hatch; afterwards
// it is where host input reaches the engine.
void I_StartFrame(void)
{
    if (booting)
    {
        longjmp(boot_escape, 1);
    }
    sg_drain_events();
}

#define MAX_ARGUMENTS 32

static char *arguments[MAX_ARGUMENTS];
static int argument_count = 0;

// The host allocates inside the module, writes its bytes, and hands the address back. Everything
// the engine needs from the outside world — the command line, the game data — arrives this way,
// because a wasm module's memory is the only place both sides can see.
__attribute__((export_name("slipgate_alloc")))
int slipgate_alloc(int size)
{
    return (int)(intptr_t)malloc((size_t)size);
}

__attribute__((export_name("slipgate_free")))
void slipgate_free(int pointer)
{
    free((void *)(intptr_t)pointer);
}

// Appends one already-written, NUL-terminated argument to the command line the engine will read.
__attribute__((export_name("slipgate_arg_push")))
int slipgate_arg_push(int pointer)
{
    if (argument_count >= MAX_ARGUMENTS)
    {
        return 0;
    }

    arguments[argument_count] = (char *)(intptr_t)pointer;
    argument_count++;
    return 1;
}

__attribute__((export_name("slipgate_init")))
int slipgate_init(void)
{
    if (booted)
    {
        return 0;
    }

    myargc = argument_count;
    myargv = arguments;

    booting = true;
    sg_host_log("slipgate: entering the engine's start-up");
    if (setjmp(boot_escape) == 0)
    {
        D_DoomMain();
        // D_DoomMain does not return; reaching here means the engine gave up during start-up.
        booting = false;
        finished = true;
        return 1;
    }
    booting = false;
    booted = true;
    sg_host_log("slipgate: engine ready");
    return 0;
}

#define MAX_DEMO_NAME 16

// The engine keeps the pointer it is given rather than a copy of the name, and the frame that name
// would live in during start-up is abandoned when the host escapes D_DoomMain. This buffer outlives
// everything, which is what makes demo playback survive to the end of the demo.
static char demo_name[MAX_DEMO_NAME];

// Starts playback of a demo lump the game data carries. Also how attract mode will run one: the
// engine's own entry point, called after boot rather than through the command line, because a name
// on the command line is copied into start-up's stack frame and that frame does not survive.
__attribute__((export_name("slipgate_play_demo")))
int slipgate_play_demo(int name_pointer, int single)
{
    if (!booted || finished)
    {
        return 0;
    }

    M_StringCopy(demo_name, (const char *)(intptr_t)name_pointer, sizeof(demo_name));
    if (W_CheckNumForName(demo_name) < 0)
    {
        sg_host_log("slipgate: that demo is not in the game data");
        return 0;
    }

    return sg_engine_play_demo(demo_name, single != 0) ? 1 : 0;
}

__attribute__((export_name("slipgate_step")))
int slipgate_step(int elapsed_millis)
{
    if (!booted || finished)
    {
        return SG_ENGINE_FINISHED;
    }

    if (setjmp(step_escape) != 0)
    {
        // The engine quit inside this frame. Whatever it drew before asking to stop still stands.
        step_escape_valid = false;
        return SG_ENGINE_FINISHED;
    }
    step_escape_valid = true;

    sg_set_elapsed_millis(sg_elapsed_millis() + elapsed_millis);
    sg_audio_advance(elapsed_millis);
    sg_engine_run_frame();
    step_escape_valid = false;

    int status = 0;
    if (sg_take_frame_complete())
    {
        status |= SG_FRAME_RENDERED;
    }
    if (sg_palette_generation() != last_palette_generation)
    {
        last_palette_generation = sg_palette_generation();
        status |= SG_PALETTE_CHANGED;
    }
    return status;
}

__attribute__((export_name("slipgate_framebuffer")))
int slipgate_framebuffer(void)
{
    return (int)(intptr_t)I_VideoBuffer;
}

__attribute__((export_name("slipgate_framebuffer_size")))
int slipgate_framebuffer_size(void)
{
    return SCREENWIDTH * SCREENHEIGHT;
}

__attribute__((export_name("slipgate_framebuffer_width")))
int slipgate_framebuffer_width(void)
{
    return SCREENWIDTH;
}

__attribute__((export_name("slipgate_framebuffer_height")))
int slipgate_framebuffer_height(void)
{
    return SCREENHEIGHT;
}

__attribute__((export_name("slipgate_palette")))
int slipgate_palette(void)
{
    return (int)(intptr_t)sg_palette_bytes();
}

__attribute__((export_name("slipgate_push_event")))
void slipgate_push_event(int type, int code, int value)
{
    sg_push_event(type, code, value);
}

__attribute__((export_name("slipgate_audio_drain")))
int slipgate_audio_drain(int destination, int frames)
{
    return sg_audio_drain((int16_t *)(intptr_t)destination, frames);
}

// Suspend and resume land later; the export exists so the host's contract does not change when
// they do, and reports honestly that it has nothing to give yet.
__attribute__((export_name("slipgate_save_state")))
int slipgate_save_state(int destination, int capacity)
{
    (void)destination;
    (void)capacity;
    return 0;
}

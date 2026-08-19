// The surface the host calls.
//
// Every gate exposes the same handful of functions regardless of which engine is behind them, so
// host/runtime sees one shape and the launcher never learns which game it is running.
//
// The interesting part is the boot: D_DoomMain never returns, because it ends in D_DoomLoop's
// infinite loop. Rather than patch the engine, the platform layer sets a jump before calling it
// and escapes from the first I_StartFrame — by which point every initialisation D_DoomLoop does
// has already run. Stepping afterwards is just calling D_RunFrame.

#include <setjmp.h>
#include <stdlib.h>
#include <string.h>

#include "config.h"
#include "doomtype.h"
#include "i_system.h"
#include "i_video.h"
#include "m_argv.h"

#include "sg_platform.h"

extern void D_DoomMain(void);
extern void D_RunFrame(void);

#define SNAPSHOT_BYTES 8

static jmp_buf boot_escape;
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
    sg_host_log("slipgate: entering D_DoomMain");
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

__attribute__((export_name("slipgate_step")))
int slipgate_step(int elapsed_millis)
{
    if (!booted || finished)
    {
        return SG_ENGINE_FINISHED;
    }

    sg_set_elapsed_millis(sg_elapsed_millis() + elapsed_millis);
    D_RunFrame();

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

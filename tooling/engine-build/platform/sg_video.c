// Video for a host that owns the screen.
//
// The engine draws into I_VideoBuffer exactly as it always has; nothing here scales, filters or
// presents. The host reads the buffer and the palette through the exported functions and decides
// what a pixel looks like, which is what lets one renderer serve every gate.

#include <limits.h>
#include <stdlib.h>
#include <string.h>

#include "config.h"
#include "deh_str.h"
#include "doomtype.h"
#include "i_video.h"
#include "m_misc.h"
#include "v_video.h"
#include "w_wad.h"
#include "z_zone.h"

#include "sg_platform.h"

pixel_t *I_VideoBuffer = NULL;
boolean screensaver_mode = false;
boolean screenvisible = true;
int vanilla_keyboard_mapping = 1;
boolean screenblocks_recalculated = false;
char *video_driver = "";
int usegamma = 0;
byte *I_VideoBuffer_storage = NULL;

static byte palette_rgb[SG_PALETTE_BYTES];
static grabmouse_callback_t grabmouse_callback = NULL;
static int palette_generation = 0;

// Whether the screen has already been set up. Hexen re-enters its own main loop once per step, and
// that loop calls I_InitGraphics on the way in — so this runs every frame there, not once. Repeating
// the palette seed below would then overwrite every palette flash the game asked for: the red of
// being hit, the gold of picking something up, gone before it could be drawn.
static boolean graphics_ready = false;

void I_InitGraphics(void)
{
    if (graphics_ready)
    {
        return;
    }
    graphics_ready = true;

    sg_host_log("slipgate: initialising graphics");
    if (I_VideoBuffer == NULL)
    {
        I_VideoBuffer_storage = (byte *)calloc(SCREENWIDTH * SCREENHEIGHT, sizeof(pixel_t));
        I_VideoBuffer = (pixel_t *)I_VideoBuffer_storage;
    }
    screenvisible = true;
    V_RestoreBuffer();

    // The palette starts as the game's own, the way upstream's I_InitGraphics ends. Doom happens to
    // set it again early enough that leaving this out was invisible; Heretic does not set one until
    // its status bar starts flashing, so without this the host is handed indexed pixels and 256
    // black entries to resolve them through.
    I_SetPalette(W_CacheLumpName(DEH_String("PLAYPAL"), PU_CACHE));
}

void I_ShutdownGraphics(void)
{
    // The buffer outlives the engine: the host may still be presenting the last frame. The flag does
    // not, so an engine that starts its screen again gets it set up again.
    graphics_ready = false;
    screenvisible = false;
}

void I_SetPalette(byte *palette)
{
    memcpy(palette_rgb, palette, sizeof(palette_rgb));
    palette_generation++;
}

int I_GetPaletteIndex(int r, int g, int b)
{
    int best = 0;
    int best_difference = INT_MAX;

    for (int index = 0; index < SG_PALETTE_ENTRIES; index++)
    {
        int dr = r - palette_rgb[index * 3 + 0];
        int dg = g - palette_rgb[index * 3 + 1];
        int db = b - palette_rgb[index * 3 + 2];
        int difference = dr * dr + dg * dg + db * db;

        if (difference < best_difference)
        {
            best_difference = difference;
            best = index;
            if (difference == 0)
            {
                break;
            }
        }
    }

    return best;
}

// The host presents when it chooses, so finishing an update means nothing more than saying the
// frame is complete.
void I_FinishUpdate(void)
{
    sg_mark_frame_complete();
}

void I_UpdateNoBlit(void)
{
}

void I_ReadScreen(pixel_t *scr)
{
    memcpy(scr, I_VideoBuffer, SCREENWIDTH * SCREENHEIGHT * sizeof(*scr));
}

void I_BeginRead(void)
{
}

void I_SetWindowTitle(const char *title)
{
    (void)title;
}

void I_GraphicsCheckCommandLine(void)
{
}

void I_SetGrabMouseCallback(grabmouse_callback_t func)
{
    grabmouse_callback = func;
}

void I_CheckIsScreensaver(void)
{
}

void I_DisplayFPSDots(boolean dots_on)
{
    (void)dots_on;
}

void I_BindVideoVariables(void)
{
}

void I_InitWindowTitle(void)
{
}

void I_RegisterWindowIcon(const unsigned int *icon, int width, int height)
{
    (void)icon;
    (void)width;
    (void)height;
}

void I_InitWindowIcon(void)
{
}

void I_EnableLoadingDisk(int xoffs, int yoffs)
{
    (void)xoffs;
    (void)yoffs;
}

const byte *sg_palette_bytes(void)
{
    return palette_rgb;
}

int sg_palette_generation(void)
{
    return palette_generation;
}

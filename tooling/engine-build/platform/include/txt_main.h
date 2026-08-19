// The slice of Chocolate Doom's textscreen library Heretic's start-up still reaches for.
//
// Heretic draws a DOS text-mode loading screen before the game starts, and its d_main.c calls the
// textscreen library directly rather than through the i_* layer. The library itself is an SDL
// window with a bitmap font in it, which has no place inside a gate.
//
// So this shim declares the handful of calls that file makes, and platform/heretic implements them
// as nothing at all. TXT_Init returning false is what the engine already treats as "this machine has
// no text mode": initStartup gives up and the game boots without the loading screen. That is a path
// the engine supports, not one this port invented.
#pragma once

#include "doomtype.h"

typedef enum
{
    TXT_COLOR_BLACK,
    TXT_COLOR_BLUE,
    TXT_COLOR_GREEN,
    TXT_COLOR_CYAN,
    TXT_COLOR_RED,
    TXT_COLOR_MAGENTA,
    TXT_COLOR_BROWN,
    TXT_COLOR_GREY,
    TXT_COLOR_DARK_GREY,
    TXT_COLOR_BRIGHT_BLUE,
    TXT_COLOR_BRIGHT_GREEN,
    TXT_COLOR_BRIGHT_CYAN,
    TXT_COLOR_BRIGHT_RED,
    TXT_COLOR_BRIGHT_MAGENTA,
    TXT_COLOR_YELLOW,
    TXT_COLOR_BRIGHT_WHITE,
} txt_color_t;

int TXT_Init(void);
void TXT_Shutdown(void);
unsigned char *TXT_GetScreenData(void);
void TXT_UpdateScreen(void);
int TXT_GetChar(void);

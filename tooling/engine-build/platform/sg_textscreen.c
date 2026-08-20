// The text-mode loading screen, absent.
//
// TXT_Init reports that there is no text mode, which is a state every engine that asks already
// handles: Heretic's initStartup clears using_graphical_startup and boots without the loading
// screen, Strife's clears using_text_startup and returns, and every other call here is then
// unreachable. They exist because the compiler cannot know that.
//
// Shared rather than per-game because it says nothing about any one engine. The per-game directories
// are for what a frame of that game is; this is the platform declining to have a text mode, which is
// the same declining whoever asks. Doom and Hexen never call it and carry the definitions unused.

#include <stddef.h>

#include "txt_io.h"
#include "txt_main.h"

int TXT_Init(void)
{
    return 0;
}

void TXT_Shutdown(void)
{
}

// A screen nobody drew has no data. The one caller reads this only after TXT_Init succeeded.
unsigned char *TXT_GetScreenData(void)
{
    return NULL;
}

void TXT_UpdateScreen(void)
{
}

// No keyboard reaches the text screen: the host's events go to the game, which is the only thing
// running by the time anything could press a key.
int TXT_GetChar(void)
{
    return 0;
}

void TXT_PutChar(int c)
{
    (void)c;
}

void TXT_Puts(const char *s)
{
    (void)s;
}

void TXT_GotoXY(int x, int y)
{
    (void)x;
    (void)y;
}

// Strife reads the cursor back to lay out its loading screen. Origin is the honest answer for a
// screen that was never opened, and the caller only ever writes relative to what it reads.
void TXT_GetXY(int *x, int *y)
{
    if (x != NULL)
    {
        *x = 0;
    }
    if (y != NULL)
    {
        *y = 0;
    }
}

void TXT_FGColor(txt_color_t color)
{
    (void)color;
}

void TXT_BGColor(int color, int blinking)
{
    (void)color;
    (void)blinking;
}

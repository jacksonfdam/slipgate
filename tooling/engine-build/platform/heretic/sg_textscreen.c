// The text-mode loading screen, absent.
//
// TXT_Init reports that there is no text mode, which is a state the engine already handles: Heretic's
// initStartup clears using_graphical_startup and boots without the loading screen, and every other
// call here is then unreachable. They exist because the compiler cannot know that.

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

void TXT_FGColor(txt_color_t color)
{
    (void)color;
}

void TXT_BGColor(int color, int blinking)
{
    (void)color;
    (void)blinking;
}

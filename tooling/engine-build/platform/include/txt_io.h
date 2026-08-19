// The writing half of the textscreen shim; see txt_main.h for why any of it exists.
#pragma once

#include "txt_main.h"

void TXT_PutChar(int c);
void TXT_Puts(const char *s);
void TXT_GotoXY(int x, int y);
void TXT_FGColor(txt_color_t color);
void TXT_BGColor(int color, int blinking);

// The slice of SDL the engines still reach for once the platform layer is replaced.
//
// Chocolate Doom's SDL usage is confined to the i_* files, which Slipgate replaces wholesale —
// except for four things the portable sources use: byte order swaps, qsort, and the preferences
// path. Shimming those is far smaller than patching the engine, and it keeps the upstream tree
// untouched, which is the point.
#pragma once

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define SDL_SwapLE16(value) ((uint16_t)(value))
#define SDL_SwapLE32(value) ((uint32_t)(value))

static inline uint16_t SDL_SwapBE16(uint16_t value)
{
    return (uint16_t)((value << 8) | (value >> 8));
}

static inline uint32_t SDL_SwapBE32(uint32_t value)
{
    return ((value & 0x000000FFu) << 24) | ((value & 0x0000FF00u) << 8) |
           ((value & 0x00FF0000u) >> 8) | ((value & 0xFF000000u) >> 24);
}

// The engine's input header declares handlers taking SDL events. Nothing in a Slipgate build ever
// constructs one — the host pushes its own events in — so an opaque type is all the shim owes it.
typedef struct SDL_Event SDL_Event;

// The joystick header extends SDL's controller button enum, so the shim owes it the value that
// enum ends at. Slipgate's own gamepad is virtual and never produces these, but the engine's
// binding tables are sized from them.
#define SDL_CONTROLLER_BUTTON_MAX 21
#define SDL_CONTROLLER_AXIS_MAX 6
#define SDL_JOYSTICK_AXIS_MAX 32767

#define SDL_qsort qsort
#define SDL_free free

// Save data lives in a directory the host mounts, so the engine's own preference path is a fixed
// location inside the virtual filesystem rather than a platform convention.
char *SDL_GetPrefPath(const char *organisation, const char *application);

// Input, pushed in rather than polled out.
//
// The host owns the keyboard, the touchscreen and the gamepad, and writes events into a queue.
// The engine drains that queue when it asks for input, so the platform layer never blocks and a
// headless run can feed a recorded sequence exactly as a player would.

#include <string.h>

#include "config.h"
#include "d_event.h"
#include "doomkeys.h"
#include "doomtype.h"
#include "i_input.h"
#include "i_video.h"

#include "sg_platform.h"

#define EVENT_QUEUE_SIZE 64

typedef struct
{
    int type;
    int code;
    int value;
} queued_event_t;

static queued_event_t queue[EVENT_QUEUE_SIZE];
static int queue_head = 0;
static int queue_tail = 0;

void sg_push_event(int type, int code, int value)
{
    int next = (queue_tail + 1) % EVENT_QUEUE_SIZE;

    // A full queue drops the oldest event rather than the newest: releases matter more than the
    // presses they follow, and a stuck key is worse than a missed one.
    if (next == queue_head)
    {
        queue_head = (queue_head + 1) % EVENT_QUEUE_SIZE;
    }

    queue[queue_tail].type = type;
    queue[queue_tail].code = code;
    queue[queue_tail].value = value;
    queue_tail = next;
}

void sg_drain_events(void)
{
    while (queue_head != queue_tail)
    {
        queued_event_t queued = queue[queue_head];
        queue_head = (queue_head + 1) % EVENT_QUEUE_SIZE;

        event_t event;
        memset(&event, 0, sizeof(event));

        switch (queued.type)
        {
            case SG_EVENT_KEY_DOWN:
                event.type = ev_keydown;
                event.data1 = queued.code;
                event.data2 = queued.value;
                event.data3 = queued.value;
                break;

            case SG_EVENT_KEY_UP:
                event.type = ev_keyup;
                event.data1 = queued.code;
                event.data2 = queued.value;
                break;

            case SG_EVENT_MOUSE_MOVE:
                event.type = ev_mouse;
                event.data2 = queued.code;
                event.data3 = queued.value;
                break;

            case SG_EVENT_MOUSE_BUTTONS:
                event.type = ev_mouse;
                event.data1 = queued.code;
                break;

            default:
                continue;
        }

        D_PostEvent(&event);
    }
}

void I_StartTic(void)
{
    sg_drain_events();
}

void I_BindInputVariables(void)
{
}

// Text input belongs to the host: it owns the on-screen keyboard, if there is one at all.
void I_StartTextInput(int x1, int y1, int x2, int y2)
{
    (void)x1;
    (void)y1;
    (void)x2;
    (void)y2;
}

void I_StopTextInput(void)
{
}

// Declared by the engine for the SDL layer that no longer exists. Events arrive through
// sg_push_event instead, so these can only ever be called with something Slipgate did not make.
void I_HandleKeyboardEvent(SDL_Event *sdlevent)
{
    (void)sdlevent;
}

void I_HandleMouseEvent(SDL_Event *sdlevent)
{
    (void)sdlevent;
}

void I_InitInput(void)
{
}

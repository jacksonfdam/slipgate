// The seam between the engine and Slipgate's host.
//
// Everything the platform layer shares with the exported surface lives here, so the engine's own
// headers stay untouched and the exports file has one place to look.
#pragma once

#include "doomtype.h"

#define SG_PALETTE_ENTRIES 256
#define SG_PALETTE_BYTES (SG_PALETTE_ENTRIES * 3)

// Frame status flags, returned by slipgate_step.
#define SG_FRAME_RENDERED 0x01
#define SG_PALETTE_CHANGED 0x02
#define SG_ENGINE_FINISHED 0x04

// Input event kinds the host pushes in. They mirror the engine's own event types rather than
// inventing a vocabulary that would need translating twice.
#define SG_EVENT_KEY_DOWN 1
#define SG_EVENT_KEY_UP 2
#define SG_EVENT_MOUSE_MOVE 3
#define SG_EVENT_MOUSE_BUTTONS 4

// Imported from the host. Declaring the module and name explicitly is what turns an undefined
// symbol into a wasm import rather than a link error.
#define SG_HOST_IMPORT(name) \
    __attribute__((import_module("slipgate"), import_name(name)))

SG_HOST_IMPORT("fatal") void sg_host_fatal(const char *message);
SG_HOST_IMPORT("log") void sg_host_log(const char *message);

const byte *sg_palette_bytes(void);
int sg_palette_generation(void);

void sg_mark_frame_complete(void);
boolean sg_take_frame_complete(void);

void sg_set_elapsed_millis(int elapsed);
int sg_elapsed_millis(void);

void sg_push_event(int type, int code, int value);
void sg_drain_events(void);

// Implemented once per engine, under platform/<game>/, because each game keeps its own main loop
// and its own demo entry point. The exported surface calls these and never names a game.
void sg_engine_run_frame(void);
boolean sg_engine_play_demo(const char *name, boolean single);

// Called at the top of every engine frame, from I_StartFrame.
//
// It exists for the engines whose frame cannot be called directly: an engine layer that has to run
// the game's own loop leaves it from here, by longjmp, once one iteration has finished. The engines
// with a frame function of their own do nothing here, and this returns.
void sg_engine_frame_boundary(void);

// Binds the weapon cycle to the codes the gates send. Called once, after the engine has loaded its
// own defaults, because that is what would otherwise overwrite it.
void sg_bind_weapon_cycle(void);

int sg_audio_drain(int16_t *destination, int frames);
void sg_audio_advance(int elapsed_millis);
void sg_request_quit(void);
void sg_audio_reset(void);

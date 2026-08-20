// Subsystems Slipgate does not use, kept as honest no-ops.
//
// Chocolate Doom expects a text screen, a CD player, a joystick, network transport and a music
// pack manager to exist. None of them has a place in a gate that draws into a host-owned
// framebuffer, and stubbing them here is what keeps the upstream tree unpatched.

#include <stdlib.h>
#include <string.h>

#include "config.h"
#include "doomtype.h"
#include "net_defs.h"
#include "net_io.h"

#include "sg_platform.h"

// Preferences live in the virtual filesystem the host mounts read-write for saves.
char *SDL_GetPrefPath(const char *organisation, const char *application)
{
    (void)organisation;
    (void)application;
    return strdup("/slipgate/");
}

// Joystick
void I_InitJoystick(void) {}
void I_ShutdownJoystick(void) {}
void I_UpdateJoystick(void) {}
void I_BindJoystickVariables(void) {}

// Text screen at exit
void I_Endoom(byte *endoom_data) { (void)endoom_data; }

// CD audio
int I_CDMusInit(void) { return -1; }
int I_CDMusPlay(int track) { (void)track; return -1; }
int I_CDMusStop(void) { return -1; }
int I_CDMusResume(void) { return -1; }
int I_CDMusSetVolume(int volume) { (void)volume; return -1; }
int I_CDMusFirstTrack(void) { return -1; }
void I_CDMusPrintStartup(void) {}
int I_CDMusLastTrack(void) { return -1; }
int I_CDMusTrackLength(int track) { (void)track; return -1; }

// High resolution text mode, used only by the setup tool
// The 640x480 planar mode Hexen shows its loading bar in. Reporting failure is a path the engine
// already handles: st_start.c skips the graphical startup and loads with nothing on screen.
boolean I_SetVideoModeHR(void) { return false; }
void I_UnsetVideoModeHR(void) {}
void I_SetWindowTitleHR(const char *title) { (void)title; }
// Nothing can abort a screen that was never shown.
boolean I_CheckAbortHR(void) { return false; }
void I_InitGraphicsHR(void) {}
void I_ShutdownGraphicsHR(void) {}
void I_SetScreenHR(void) {}
void I_ClearScreenHR(void) {}
void I_SlamBlockHR(int x, int y, int w, int h, const byte *src) {
    (void)x; (void)y; (void)w; (void)h; (void)src;
}
void I_SlamHR(const byte *buffer) { (void)buffer; }
void I_InitPaletteHR(void) {}
void I_SetPaletteHR(const byte *palette) { (void)palette; }
void I_FadeToPaletteHR(const byte *palette) { (void)palette; }
void I_BlackPaletteHR(void) {}
void I_ReadScreenHR(byte *scr) { (void)scr; }
void I_BeginReadHR(void) {}
void I_EndReadHR(void) {}

// Networking: a gate is single player, and saying so plainly beats a transport that never
// connects.
static boolean net_never_ready(net_addr_t **addr, net_packet_t **packet)
{
    (void)addr;
    (void)packet;
    return false;
}

// A gate is single player, so both roles decline rather than pretending to start.
static boolean net_no_client(void) { return false; }
static boolean net_no_server(void) { return false; }
static void net_no_send(net_addr_t *addr, net_packet_t *packet) { (void)addr; (void)packet; }
static net_addr_t *net_no_resolve(const char *address) { (void)address; return NULL; }
static void net_no_free(net_addr_t *addr) { (void)addr; }
static void net_no_addr_to_string(net_addr_t *addr, char *buffer, int size)
{
    (void)addr;
    if (size > 0)
    {
        buffer[0] = '\0';
    }
}

net_module_t net_sdl_module = {
    net_no_client,
    net_no_server,
    net_no_send,
    net_never_ready,
    net_no_addr_to_string,
    net_no_free,
    net_no_resolve,
};

void NET_SDL_AddrToString(net_addr_t *addr, char *buffer, int buffer_len)
{
    net_no_addr_to_string(addr, buffer, buffer_len);
}

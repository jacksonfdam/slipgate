// Configuration variables the replaced platform files used to own.
//
// The engine reads these from every corner of its code, and they belong to the i_* layer that
// Slipgate replaces. They live together here rather than being scattered through the platform
// files, because what they have in common is where they came from, not what they do.

#include "config.h"
#include "doomtype.h"

// Mouse. Slipgate feeds movement through its own event queue, so the acceleration curve the engine
// would apply is left at the value that changes nothing.
int usemouse = 0;
float mouse_acceleration = 2.0f;
int mouse_threshold = 10;

// Gamepad. The virtual pad is the host's, and it sends key events rather than axes, so the
// engine's own joystick handling stays switched off.
int use_analog = 0;
int joystick_turn_sensitivity = 10;
int joystick_move_sensitivity = 10;
unsigned int joywait = 0;

// Network launch waits for other players to be ready. A gate has none.
void NET_WaitForLaunch(void)
{
}

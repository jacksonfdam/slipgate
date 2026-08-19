// Configuration variables Heretic reads that Doom does not.
//
// Same story as platform/sg_variables.c: they belonged to the i_* layer Slipgate replaces. They
// live here rather than beside Doom's so that adding a gate cannot change the module another gate
// ships.

// Heretic's own look control. The virtual pad sends key events rather than axes, so the engine's
// joystick handling stays switched off and this is the value that changes nothing.
int joystick_look_sensitivity = 10;

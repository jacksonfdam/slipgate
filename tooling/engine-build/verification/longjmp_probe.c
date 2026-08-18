// Minimal probe for the one question that blocks the whole engine port: does a module built with
// wasm exceptions and wasm longjmp actually load and run under Chasm?
//
// The engines use setjmp/longjmp for their error paths, so if this cannot run, nothing downstream
// can. It is deliberately tiny: no libc beyond setjmp.h, so the module has no imports to satisfy
// and a failure means the feature rather than the environment.

#include <setjmp.h>

static jmp_buf escape;

static void jump_back(int value) {
    longjmp(escape, value);
}

// Returns the value handed to longjmp, so a correct run answers with exactly what was thrown.
__attribute__((export_name("probe_longjmp")))
int probe_longjmp(int value) {
    int landed = setjmp(escape);
    if (landed == 0) {
        jump_back(value);
    }
    return landed;
}

// A plain call, to tell "the module did not load" apart from "longjmp did not work".
__attribute__((export_name("probe_add")))
int probe_add(int left, int right) {
    return left + right;
}

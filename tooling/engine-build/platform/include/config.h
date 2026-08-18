// Configuration the engines expect from their own build system, which Slipgate does not use.
// Only the values the sources actually read are here; anything absent is genuinely unused.
#pragma once

#define PACKAGE_NAME "Slipgate"
#define PACKAGE_TARNAME "slipgate"
#define PACKAGE_VERSION "0.1.0"
#define PACKAGE_STRING "Slipgate 0.1.0"
#define PROGRAM_PREFIX "slipgate-"

#define HAVE_DIRENT_H 1

// Without these the engine falls back to the Windows spellings of the case-insensitive string
// comparisons, which do not exist here.
#define HAVE_DECL_STRCASECMP 1
#define HAVE_DECL_STRNCASECMP 1

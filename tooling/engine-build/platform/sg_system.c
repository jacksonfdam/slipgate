// System services, minus the system.
//
// The engine expects to allocate a zone, print a banner, register exit handlers and die loudly.
// Slipgate keeps all of that except dying: a fatal error unwinds back to the host instead of
// calling exit, because a wasm instance that exits is an instance the host cannot ask anything.

#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "config.h"
#include "doomtype.h"
#include "i_system.h"
#include "m_argv.h"
#include "m_misc.h"

#include "sg_platform.h"

#define DEFAULT_ZONE_MEGABYTES 16
#define BYTES_PER_MEGABYTE (1024 * 1024)
#define MAX_EXIT_FUNCTIONS 32
#define ERROR_TEXT_LIMIT 512

typedef struct
{
    atexit_func_t function;
    boolean run_on_error;
} exit_entry_t;

static exit_entry_t exit_functions[MAX_EXIT_FUNCTIONS];
static int exit_function_count = 0;
static char last_error[ERROR_TEXT_LIMIT];
static boolean has_error = false;

void I_Init(void)
{
}

byte *I_ZoneBase(int *size)
{
    int megabytes = DEFAULT_ZONE_MEGABYTES;
    int parameter = M_CheckParmWithArgs("-mb", 1);

    if (parameter > 0)
    {
        megabytes = atoi(myargv[parameter + 1]);
    }

    *size = megabytes * BYTES_PER_MEGABYTE;
    byte *zone = (byte *)malloc(*size);

    if (zone == NULL)
    {
        I_Error("failed to allocate %d MiB of zone memory", megabytes);
    }

    return zone;
}

boolean I_ConsoleStdout(void)
{
    return false;
}

void I_AtExit(atexit_func_t func, boolean run_if_error)
{
    if (exit_function_count >= MAX_EXIT_FUNCTIONS)
    {
        return;
    }

    exit_functions[exit_function_count].function = func;
    exit_functions[exit_function_count].run_on_error = run_if_error;
    exit_function_count++;
}

static void run_exit_functions(boolean erroring)
{
    for (int index = exit_function_count - 1; index >= 0; index--)
    {
        if (!erroring || exit_functions[index].run_on_error)
        {
            exit_functions[index].function();
        }
    }
    exit_function_count = 0;
}

void I_Quit(void)
{
    run_exit_functions(false);
    sg_host_fatal("the engine quit");
    // sg_host_fatal does not return; the host tears the instance down.
    for (;;)
    {
    }
}

void I_Error(const char *error, ...)
{
    va_list arguments;

    if (!has_error)
    {
        has_error = true;
        va_start(arguments, error);
        M_vsnprintf(last_error, sizeof(last_error), error, arguments);
        va_end(arguments);
        run_exit_functions(true);
    }

    sg_host_fatal(last_error);
    for (;;)
    {
    }
}

void I_Tactile(int on, int off, int total)
{
    (void)on;
    (void)off;
    (void)total;
}

void *I_Realloc(void *ptr, size_t size)
{
    void *result = realloc(ptr, size);

    if (result == NULL && size != 0)
    {
        I_Error("failed to reallocate %zu bytes", size);
    }

    return result;
}

boolean I_GetMemoryValue(unsigned int offset, void *value, int size)
{
    (void)offset;
    (void)value;
    (void)size;
    return false;
}

void I_BindVariables(void)
{
}

void I_PrintBanner(const char *text)
{
    sg_host_log(text);
}

void I_PrintDivider(void)
{
    sg_host_log("--------------------------------------------------------------");
}

void I_PrintStartupBanner(const char *gamedescription)
{
    I_PrintDivider();
    I_PrintBanner(gamedescription);
    I_PrintDivider();
}

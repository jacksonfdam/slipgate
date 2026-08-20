// Reading a directory, over a filesystem that has none.
//
// Upstream's i_glob.c is opendir and readdir. The module's filesystem is the table in sg_files.c —
// full paths and bytes, no directory entries to open — so opendir reaches Emscripten's real
// filesystem instead, finds nothing, and hands back NULL. Strife is the first engine here to care:
// ClearTmp globs its temporary save directory at start-up and calls I_Error when the glob is NULL,
// which is a gate that dies before its first frame.
//
// A directory is therefore a prefix rather than a thing. Any path inside the save area globs
// successfully, whether or not a file currently lives under it, because an empty temporary directory
// is the normal state at start-up and not an error. Anything outside it globs empty, which is what a
// module with no music packs and no autoload directory should report.

#include <stdarg.h>
#include <stddef.h>
#include <string.h>
#include <strings.h>

#include "doomtype.h"
#include "i_glob.h"
#include "m_misc.h"

#include "sg_platform.h"

#define MAX_PATTERNS 8

// Enough for the longest path the save area holds; matches sg_files.c.
#define MAX_GLOB_PATH 128

struct glob_s
{
    boolean used;
    char directory[MAX_GLOB_PATH];
    const char *patterns[MAX_PATTERNS];
    char pattern_storage[MAX_PATTERNS][MAX_GLOB_PATH];
    int pattern_count;
    int flags;
    int next;
    char last[MAX_GLOB_PATH];
};

// One at a time is all any caller here needs: every glob in the engine is opened, drained and ended
// inside one function. A second concurrent glob would be a caller doing something this port has
// never seen, and it gets NULL rather than a quietly shared cursor.
static glob_t the_glob;

/** Whether [name] sits directly inside [directory] rather than deeper under it. */
static boolean directly_inside(const char *name, const char *directory)
{
    size_t length = strlen(directory);
    if (strncmp(name, directory, length) != 0)
    {
        return false;
    }
    const char *rest = name + length;
    if (*rest == '/')
    {
        rest++;
    }
    else if (length > 0 && directory[length - 1] != '/')
    {
        return false;
    }
    return *rest != '\0' && strchr(rest, '/') == NULL;
}

/** Upstream's own matcher, reduced to what the patterns here actually use: `*` and literals. */
static boolean matches(const char *name, const char *pattern, boolean ignore_case)
{
    if (pattern[0] == '*' && pattern[1] == '\0')
    {
        return true;
    }

    // Every other pattern the engine passes is "*.ext", so a suffix test is the whole of it.
    if (pattern[0] == '*')
    {
        const char *suffix = pattern + 1;
        size_t name_length = strlen(name);
        size_t suffix_length = strlen(suffix);
        if (suffix_length > name_length)
        {
            return false;
        }
        const char *tail = name + name_length - suffix_length;
        return ignore_case ? strcasecmp(tail, suffix) == 0 : strcmp(tail, suffix) == 0;
    }

    return ignore_case ? strcasecmp(name, pattern) == 0 : strcmp(name, pattern) == 0;
}

static boolean matches_any(const glob_t *glob, const char *name)
{
    boolean ignore_case = (glob->flags & GLOB_FLAG_NOCASE) != 0;
    for (int index = 0; index < glob->pattern_count; index++)
    {
        if (matches(name, glob->patterns[index], ignore_case))
        {
            return true;
        }
    }
    return false;
}

static glob_t *begin(const char *directory, int flags)
{
    if (directory == NULL || the_glob.used || !sg_path_is_ours(directory))
    {
        return NULL;
    }

    memset(&the_glob, 0, sizeof(the_glob));
    the_glob.used = true;
    the_glob.flags = flags;
    M_StringCopy(the_glob.directory, directory, sizeof(the_glob.directory));
    return &the_glob;
}

static void add_pattern(glob_t *glob, const char *pattern)
{
    if (glob->pattern_count >= MAX_PATTERNS)
    {
        return;
    }
    int index = glob->pattern_count;
    M_StringCopy(glob->pattern_storage[index], pattern, sizeof(glob->pattern_storage[index]));
    glob->patterns[index] = glob->pattern_storage[index];
    glob->pattern_count++;
}

glob_t *I_StartGlob(const char *directory, const char *glob, int flags)
{
    glob_t *result = begin(directory, flags);
    if (result == NULL)
    {
        return NULL;
    }
    add_pattern(result, glob);
    return result;
}

glob_t *I_StartMultiGlob(const char *directory, int flags, const char *glob, ...)
{
    glob_t *result = begin(directory, flags);
    if (result == NULL)
    {
        return NULL;
    }

    add_pattern(result, glob);

    va_list rest;
    va_start(rest, glob);
    for (;;)
    {
        const char *pattern = va_arg(rest, const char *);
        if (pattern == NULL)
        {
            break;
        }
        add_pattern(result, pattern);
    }
    va_end(rest);

    return result;
}

void I_EndGlob(glob_t *glob)
{
    if (glob != NULL)
    {
        glob->used = false;
    }
}

// The full path, because that is what upstream returns and what M_remove is then handed.
//
// GLOB_FLAG_SORTED is accepted and ignored: the file table is walked in insertion order, and the one
// caller that asks for sorting is loading a directory of add-ons this port does not have.
const char *I_NextGlob(glob_t *glob)
{
    if (glob == NULL)
    {
        return NULL;
    }

    int count = sg_file_count();
    while (glob->next < count)
    {
        const char *path = sg_file_path(glob->next);
        glob->next++;
        if (path == NULL)
        {
            continue;
        }

        const char *name = strrchr(path, '/');
        name = (name == NULL) ? path : name + 1;

        if (directly_inside(path, glob->directory) && matches_any(glob, name))
        {
            M_StringCopy(glob->last, path, sizeof(glob->last));
            return glob->last;
        }
    }

    return NULL;
}

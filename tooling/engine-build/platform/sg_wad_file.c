// WAD files that live in memory the host wrote.
//
// The engine opens WADs through a small class interface with three functions, and this replaces the
// stdio implementation rather than the filesystem underneath it. That matters: a wasm module's
// filesystem is inside the module, so a host cannot put a file into it from outside, but it can
// write bytes straight into linear memory and say what they are called.
//
// The symbol keeps the engine's own name, so w_file.c — which lists the available classes — stays
// untouched.

#include <stdlib.h>
#include <string.h>

#include "config.h"
#include "doomtype.h"
#include "m_misc.h"
#include "w_file.h"
#include "z_zone.h"

#include "sg_platform.h"

#define MAX_MOUNTED_FILES 8
#define MAX_MOUNT_NAME 64

typedef struct
{
    char name[MAX_MOUNT_NAME];
    byte *data;
    unsigned int length;
    boolean used;
} mounted_file_t;

typedef struct
{
    wad_file_t wad;
    mounted_file_t *source;
} memory_wad_file_t;

static mounted_file_t mounts[MAX_MOUNTED_FILES];

extern wad_file_class_t stdc_wad_file;

static mounted_file_t *find_mount(const char *path)
{
    // The engine passes whatever path it was given; the host mounts by base name, because a
    // virtual filesystem has no directories worth honouring.
    const char *name = path;
    for (const char *cursor = path; *cursor != '\0'; cursor++)
    {
        if (*cursor == '/' || *cursor == '\\')
        {
            name = cursor + 1;
        }
    }

    for (int index = 0; index < MAX_MOUNTED_FILES; index++)
    {
        if (mounts[index].used && strcmp(mounts[index].name, name) == 0)
        {
            return &mounts[index];
        }
    }

    return NULL;
}

static wad_file_t *sg_open_wad(const char *path)
{
    sg_host_log("slipgate: opening a wad");
    mounted_file_t *mount = find_mount(path);

    if (mount == NULL)
    {
        sg_host_log("slipgate: that wad is not mounted");
        return NULL;
    }

    memory_wad_file_t *file = Z_Malloc(sizeof(memory_wad_file_t), PU_STATIC, 0);
    file->wad.file_class = &stdc_wad_file;
    // Mapped memory is exactly what this is, so the engine may read lumps directly rather than
    // copying them through Read.
    file->wad.mapped = mount->data;
    file->wad.length = mount->length;
    file->wad.path = M_StringDuplicate(path);
    file->source = mount;

    return &file->wad;
}

static void sg_close_wad(wad_file_t *wad)
{
    Z_Free(wad);
}

static size_t sg_read_wad(wad_file_t *wad, unsigned int offset, void *buffer, size_t buffer_len)
{
    memory_wad_file_t *file = (memory_wad_file_t *)wad;

    if (offset >= file->source->length)
    {
        return 0;
    }

    size_t available = file->source->length - offset;
    size_t to_read = buffer_len < available ? buffer_len : available;
    memcpy(buffer, file->source->data + offset, to_read);

    return to_read;
}

wad_file_class_t stdc_wad_file = {
    sg_open_wad,
    sg_close_wad,
    sg_read_wad,
};

// The engine looks for its game data on a filesystem before it opens it, and a mounted file is not
// on one. Wrapping the existence check at link time — rather than patching d_iwad.c — is what lets
// the search find what the host mounted while every other path keeps working.
char *__real_M_FileCaseExists(const char *path);

char *__wrap_M_FileCaseExists(const char *path)
{
    if (find_mount(path) != NULL)
    {
        return M_StringDuplicate(path);
    }

    return __real_M_FileCaseExists(path);
}

// Mounts a file the host has already written into module memory. The host allocates through
// slipgate_alloc, writes the bytes, and hands the address over; the module keeps it for the life of
// the session.
__attribute__((export_name("slipgate_mount")))
int slipgate_mount(int name_ptr, int data_ptr, int size)
{
    const char *name = (const char *)(intptr_t)name_ptr;

    for (int index = 0; index < MAX_MOUNTED_FILES; index++)
    {
        if (mounts[index].used)
        {
            continue;
        }

        mounts[index].used = true;
        mounts[index].data = (byte *)(intptr_t)data_ptr;
        mounts[index].length = (unsigned int)size;
        strncpy(mounts[index].name, name, MAX_MOUNT_NAME - 1);
        mounts[index].name[MAX_MOUNT_NAME - 1] = '\0';

        return 1;
    }

    return 0;
}

// Forgets every mount. The buffers belong to the host, which frees them through slipgate_free.
__attribute__((export_name("slipgate_mount_clear")))
void slipgate_mount_clear(void)
{
    for (int index = 0; index < MAX_MOUNTED_FILES; index++)
    {
        if (mounts[index].used)
        {
            mounts[index].used = false;
            mounts[index].data = NULL;
            mounts[index].length = 0;
        }
    }
}

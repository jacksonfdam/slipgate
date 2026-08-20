// The engine's own files, handed across the sandbox.
//
// The files themselves live in platform/sg_files.c, which is where the engines write them. This is
// only the surface the host calls: what is there, how big it is, and its bytes in both directions.
//
// Read afterwards rather than intercepted as it happens. The engine saves by writing a file, and a
// host that wanted to catch those writes would have to know when a save was finished; asking for the
// directory once the player leaves is the same information for none of the guessing.

#include <string.h>

#include "doomtype.h"

#include "sg_platform.h"

// Rebuilds nothing: the table is the filesystem. Returns how many files the engine has written.
SG_EXPORT("slipgate_save_scan")
int slipgate_save_scan(void)
{
    return sg_file_count();
}

SG_EXPORT("slipgate_save_size")
int slipgate_save_size(int index)
{
    return (int)sg_file_size(index);
}

// Copies the name of file [index] into the host's buffer. Returns its length, or -1.
SG_EXPORT("slipgate_save_name")
int slipgate_save_name(int index, sg_ptr destination, int capacity)
{
    const char *name = sg_file_name(index);
    if (name == NULL)
    {
        return -1;
    }
    size_t length = strlen(name);
    if ((int)length + 1 > capacity)
    {
        return -1;
    }
    memcpy((char *)(intptr_t)destination, name, length + 1);
    return (int)length;
}

// Copies the contents of file [index] into the host's buffer. Returns the bytes read, or -1.
SG_EXPORT("slipgate_save_read")
int slipgate_save_read(int index, sg_ptr destination, int capacity)
{
    return (int)sg_file_read(index, (unsigned char *)(intptr_t)destination, capacity);
}

// Writes one file the host kept back into the engine's filesystem, before it looks for one.
SG_EXPORT("slipgate_save_put")
int slipgate_save_put(sg_ptr name_pointer, sg_ptr data_pointer, int size)
{
    const char *name = (const char *)(intptr_t)name_pointer;
    if (name[0] == '/' || strstr(name, "..") != NULL)
    {
        // A name that climbs out of the save directory is not a save. The host builds these from what
        // it stored, but the check costs nothing and the failure would be silent.
        return 0;
    }
    return sg_file_write(name, (const unsigned char *)(intptr_t)data_pointer, size) ? 1 : 0;
}

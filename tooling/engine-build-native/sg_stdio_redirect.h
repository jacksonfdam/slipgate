// Reroutes the engine's stdio calls to the platform layer's in-memory files on the native build.
//
// The wasm link does this with -Wl,--wrap, which Mach-O's linker does not have, so natively the
// same reroute is a macro: this header is force-included into every compilation unit except
// platform/sg_files.c, which defines the __wrap_ functions and must keep calling the C library's
// own. The ten names below are exactly the ten the wasm build wraps — replaced-sources.txt keeps
// the source lists aligned, this header keeps the reroute aligned.
#pragma once

#include <stdio.h>
#include <sys/stat.h>

FILE *__wrap_fopen(const char *path, const char *mode);
int __wrap_fclose(FILE *stream);
size_t __wrap_fread(void *buffer, size_t size, size_t count, FILE *stream);
size_t __wrap_fwrite(const void *buffer, size_t size, size_t count, FILE *stream);
int __wrap_fseek(FILE *stream, long offset, int whence);
long __wrap_ftell(FILE *stream);
int __wrap_feof(FILE *stream);
int __wrap_fgetc(FILE *stream);
int __wrap_remove(const char *path);
int __wrap_rename(const char *from, const char *to);
int __wrap_mkdir(const char *path, mode_t mode);

#define fopen __wrap_fopen
#define fclose __wrap_fclose
#define fread __wrap_fread
#define fwrite __wrap_fwrite
#define fseek __wrap_fseek
#define ftell __wrap_ftell
#define feof __wrap_feof
#define fgetc __wrap_fgetc
#define remove __wrap_remove
#define rename __wrap_rename
#define mkdir __wrap_mkdir

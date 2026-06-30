#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#    include <winsock2.h>
#    define le32_to_host(x) ( \
        (*(const uint16_t *)"\0\xff" < 0x100) ? (x) : _byteswap_ulong(x))
#else
#    include <endian.h>
#    define le32_to_host(x) le32toh(x)
#endif

#include "CStandardFileIO.h"

int read_full(FILE *f, void *buf, size_t size)
{
    unsigned char *p = buf;

    while (size > 0) {
        size_t n = fread(p, 1, size, f);

        if (n == 0) {
            if (ferror(f))
                return -1;

            errno = EIO;
            return -1;
        }

        p += n;
        size -= n;
    }

    return 0;
}

int load_le_u32_file(const char *path, u32_file *out)
{
    memset(out, 0, sizeof(*out));

    FILE *f = fopen(path, "rb");
    if (!f)
        return -1;

    if (fseek(f, 0, SEEK_END) != 0) {
        fclose(f);
        return -1;
    }

    long file_size = ftell(f);
    if (file_size < 0) {
        fclose(f);
        return -1;
    }

    if ((file_size % 4) != 0) {
        fclose(f);
        errno = EINVAL;
        return -1;
    }

    if (fseek(f, 0, SEEK_SET) != 0) {
        fclose(f);
        return -1;
    }

    size_t count = (size_t)file_size / sizeof(uint32_t);

    if (count > SIZE_MAX / sizeof(uint32_t)) {
        fclose(f);
        errno = EOVERFLOW;
        return -1;
    }

    uint32_t *data = NULL;

    if (count != 0) {
        data = malloc(count * sizeof(uint32_t));
        if (!data) {
            fclose(f);
            return -1;
        }

        if (read_full(f, data, count * sizeof(uint32_t)) != 0) {
            free(data);
            fclose(f);
            return -1;
        }

        if (le32_to_host(1) != 1) {
            for (size_t i = 0; i < count; i++) {
                data[i] = le32_to_host(data[i]);
            }
        }
    }

    if (fclose(f) != 0) {
        free(data);
        return -1;
    }

    out->data = data;
    out->len  = count;

    return 0;
}

void free_u32_file(u32_file *f)
{
    free(f->data);
    f->data = NULL;
    f->len  = 0;
}
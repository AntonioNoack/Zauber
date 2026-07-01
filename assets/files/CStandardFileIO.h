#pragma once

typedef struct {
    uint32_t *data;
    size_t len;
} u32_file;

int load_le_u32_file(const char *path, u32_file *out);
void free_u32_file(u32_file *f);
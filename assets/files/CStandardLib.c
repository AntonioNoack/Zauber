// for calloc
#include <stdlib.h>
// for booleans
#include <stdbool.h>
// for integers with precise size
#include <stdint.h>
// for perror
#include <stdio.h>

#include "CStandardFileIO.h"

typedef struct {
    uint32_t classIndex;
} GCInstance;

void* gcNew(size_t size, uint32_t classIndex) {
    void* instance = calloc(1, size);
    ((GCInstance*) instance)->classIndex = classIndex;
    return instance;
}

u32_file classTable;
u32_file interfaceTable;
u32_file superClassTable;
u32_file classToInterfaceTable;

int32_t zauber_inheritance_readFromClassTable_2clr50n(void* self, int32_t index) {
    if (index < 0 || index >= classTable.len) {
        perror("Index into classTable out of bounds");
        exit(1);
    }
    return classTable.data[index];
}

int32_t zauber_inheritance_readFromInterfaceTable_2clr50n(void* self, int32_t index) {
    if (index < 0 || index >= interfaceTable.len) {
        perror("Index into interfaceTable out of bounds");
        exit(1);
    }
    return interfaceTable.data[index];
}

int32_t zauber_inheritance_readFromSuperClassTable_2clr50n(void* self, int32_t index) {
    if (index < 0 || index >= superClassTable.len) {
        perror("Index into superClassTable out of bounds");
        exit(1);
    }
    return superClassTable.data[index];
}

int32_t zauber_inheritance_readFromClassToInterfaceTable_2clr50n(void* self, int32_t index) {
    if (index < 0 || index >= classToInterfaceTable.len) {
        perror("Index into classToInterfaceTable out of bounds");
        exit(1);
    }
    return classToInterfaceTable.data[index];
}

int32_t stdlibMain() {
    if (load_le_u32_file("classTable.bin", &classTable) != 0) {
        perror("Failed loading classTable.bin");
        return 1;
    }

    if (load_le_u32_file("interfaceTable.bin", &interfaceTable) != 0) {
        perror("Failed loading interfaceTable.bin");
        return 1;
    }

    if (load_le_u32_file("superClassTable.bin", &superClassTable) != 0) {
        perror("Failed loading superClassTable.bin");
        return 1;
    }

    if (load_le_u32_file("classToInterfaceTable.bin", &classToInterfaceTable) != 0) {
        perror("Failed loading classToInterfaceTable.bin");
        return 1;
    }

    return 0;
}
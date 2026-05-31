#pragma once

// for calloc
#include <stdlib.h>
// for booleans
#include <stdbool.h>
// for integers with precise size
#include <stdint.h>

void* gcNew(size_t size, uint32_t classIndex);
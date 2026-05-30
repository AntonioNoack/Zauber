#pragma once

// for calloc
#include <stdlib.h>
// for booleans
#include <stdbool.h>
// for integers with precise size
#include <stdint.h>

#define gcNew(T) (T*) calloc(1, sizeof(T))

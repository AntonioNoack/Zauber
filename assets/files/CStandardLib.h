#pragma once

// for calloc
#include <stdlib.h>
// for booleans
#include <stdbool.h>

#define gcNew(T) (T*) calloc(1, sizeof(T))

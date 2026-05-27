#pragma once

// for calloc
#include <stdlib.h>

#define gcNew(T) (T*) calloc(1, sizeof(T))

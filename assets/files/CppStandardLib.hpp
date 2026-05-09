#pragma once

// for calloc
#include <stdlib.h>

// for std::forward and custom-new
#include <utility>
#include <new>

template<typename T, typename... Args>
T& gcNew(Args&&... args)
{
    void* mem = calloc(1, sizeof(T));
    return *new (mem) T(std::forward<Args>(args)...);
}
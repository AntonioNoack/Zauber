package me.anno.support.cpp.ast.rich

import me.anno.support.Language

enum class CppStandard {
    C89, C99, C11, C17,
    CPP98, CPP03, CPP11, CPP14, CPP17, CPP20, CPP23;

    fun kind(): Language {
        return when (this) {
            C89, C99, C11, C17 -> Language.C
            else -> Language.CPP
        }
    }
}
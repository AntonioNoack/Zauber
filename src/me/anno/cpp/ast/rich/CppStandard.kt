package me.anno.cpp.ast.rich

enum class CppStandard {
    C89, C99, C11, C17,
    CPP98, CPP03, CPP11, CPP14, CPP17, CPP20, CPP23;

    fun kind(): LanguageKind {
        return when (this) {
            C89, C99, C11, C17 -> LanguageKind.C
            else -> LanguageKind.CPP
        }
    }
}
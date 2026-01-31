package me.anno.langserver

enum class VSCodeModifier(val code: String) {
    DECLARATION("declaration"), // declarations of symbols.
    DEFINITION("definition"), // definitions of symbols, "), // example, in header files.
    READONLY("readonly"), // readonly variables and member fields (constants).
    STATIC("static"), // class members (static members).
    DEPRECATED("deprecated"), // symbols that should no longer be used.
    ABSTRACT("abstract"), // types and member functions that are abstract.
    ASYNC("async"), // functions that are marked async.
    MODIFICATION("modification"), // variable references where the variable is assigned to.
    DOCUMENTATION("documentation"), // occurrences of symbols in documentation.
    DEFAULT_LIBRARY("defaultLibrary"), // symbols that are part of the standard library.
    ;

    val flag = 1 shl ordinal
}
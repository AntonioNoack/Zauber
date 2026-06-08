package me.anno.zauber.ast.rich.parameter

enum class ParameterMutability {
    /**
     * mutable
     * and stored
     * */
    VAR,

    /**
     * immutable (reference at least)
     * and stored
     * */
    VAL,

    /**
     * compile-time,
     * stored indirectly
     * */
    CONST,

    /**
     * immutable (reference at least),
     * but not stored
     * */
    DEFAULT,
}
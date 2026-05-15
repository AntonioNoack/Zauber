package me.anno.zauber.ast.rich

enum class ParameterExpansion {
    /**
     * no expansion
     * */
    NONE,
    /**
     * for Zauber; only one parameter can have this property
     * todo validate that
     * */
    VARARG,
    /**
     * for Python
     * */
    VARDICT,
}
package me.anno.zauber.ast.rich

enum class InnerSuperCallTarget {
    /**
     * calls a constructor in this class
     * */
    THIS,

    /**
     * calls a constructor in the parent class
     * */
    SUPER
}

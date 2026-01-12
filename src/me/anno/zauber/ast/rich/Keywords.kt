package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet

object Keywords {
    const val NONE = 0
    const val SYNTHETIC = 1
    const val PUBLIC = 2
    const val PRIVATE = 4
    const val PROTECTED = 8
    const val OVERRIDE = 16
    const val OPEN = 32
    const val FINAL = 64
    const val ABSTRACT = 128
    const val DATA_CLASS = 256
    const val VALUE = 512
    const val FUN_INTERFACE = 1024
    const val EXTERNAL = 2 * 1024
    const val OPERATOR = 4 * 1024
    const val INLINE = 8 * 1024
    const val CROSS_INLINE = 16 * 1024
    const val INFIX = 32 * 1024

    /**
     * class used for annotations;
     * is a pure value class, aka all members must be values and their properties must be, too
     * */
    const val ANNOTATION = 64 * 1024

    /**
     * class cannot be extended by classes from other packages/modules
     * */
    const val SEALED = 128 * 1024

    /**
     * evaluated at compile time
     * */
    const val CONSTEXPR = 256 * 1024

    const val CPP_STRUCT = 1024 * 1024

    fun KeywordSet.hasFlag(flag: KeywordSet): Boolean {
        return (this and flag) == flag
    }
}
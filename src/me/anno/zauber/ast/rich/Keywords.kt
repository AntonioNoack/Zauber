package me.anno.zauber.ast.rich

import me.anno.zauber.ast.KeywordSet
import kotlin.math.max

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

    fun KeywordSet.withFlag(flag: KeywordSet): KeywordSet {
        return this or flag
    }

    fun KeywordSet.withoutFlag(flag: KeywordSet): KeywordSet {
        return this and flag.inv()
    }

    fun toString(flags: KeywordSet): String {
        val builder = StringBuilder()
        if (flags.hasFlag(SYNTHETIC)) builder.append("synthetic ")
        if (flags.hasFlag(PUBLIC)) builder.append("public ")
        if (flags.hasFlag(PRIVATE)) builder.append("private ")
        if (flags.hasFlag(PROTECTED)) builder.append("protected ")
        if (flags.hasFlag(OVERRIDE)) builder.append("override ")
        if (flags.hasFlag(FINAL)) builder.append("final ")
        if (flags.hasFlag(ABSTRACT)) builder.append("abstract ")
        if (flags.hasFlag(DATA_CLASS)) builder.append("data ")
        if (flags.hasFlag(VALUE)) builder.append("value ")
        if (flags.hasFlag(FUN_INTERFACE)) builder.append("fun-interface ")
        if (flags.hasFlag(CONSTEXPR)) builder.append("const ")
        if (flags.hasFlag(EXTERNAL)) builder.append("external ")
        if (flags.hasFlag(OPERATOR)) builder.append("operator ")
        if (flags.hasFlag(INLINE)) builder.append("inline ")
        if (flags.hasFlag(INFIX)) builder.append("infix ")
        if (flags.hasFlag(ANNOTATION)) builder.append("annotation")
        if (flags.hasFlag(CROSS_INLINE)) builder.append("cross-inline ")
        if (flags.hasFlag(SEALED)) builder.append("sealed ")
        if (flags.hasFlag(CPP_STRUCT)) builder.append("struct ")

        builder.setLength(max(builder.length - 1, 0))
        return builder.toString()
    }
}
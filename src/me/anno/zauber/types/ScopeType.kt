package me.anno.zauber.types

enum class ScopeType {
    // structural
    PACKAGE,
    TYPE_ALIAS,

    // classes
    NORMAL_CLASS,
    INLINE_CLASS,
    INNER_CLASS,

    INTERFACE,
    ENUM_CLASS,  // limited instances, val name: String, val ordinal: Int, fun entries()
    ENUM_ENTRY_CLASS, // objects with enum_class as type

    OBJECT,
    COMPANION_OBJECT,

    // methods
    /**
     * definition space for method arguments
     * */
    METHOD,

    /**
     * definition space for constructor arguments
     * */
    CONSTRUCTOR,

    FIELD_GETTER,
    FIELD_SETTER,
    LAMBDA,
    // METHOD_BODY,

    // inside expressions
    METHOD_BODY,
    WHEN_CASES,
    WHEN_ELSE;

    fun isClassType(): Boolean {
        return when (this) {
            NORMAL_CLASS, ENUM_CLASS, INLINE_CLASS,
            INTERFACE,
            OBJECT, COMPANION_OBJECT -> true
            else -> false
        }
    }

    fun isInsideExpression(): Boolean {
        return when (this) {
            FIELD_GETTER,
            FIELD_SETTER,
            LAMBDA,
            METHOD_BODY,
            WHEN_CASES,
            WHEN_ELSE -> true
            else -> false
        }
    }

    companion object {
        val EXPRESSION = METHOD_BODY
    }
}
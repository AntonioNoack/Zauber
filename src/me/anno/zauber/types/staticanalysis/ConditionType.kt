package me.anno.zauber.types.staticanalysis

enum class ConditionType {
        EQUALS_CONSTANT,
        NOT_EQUALS_CONSTANT,

        INSTANCEOF,
        NOT_INSTANCEOF,

        GREATER_THAN,
        GREATER_THAN_OR_EQUALS,
        LESS_THAN,
        LESS_THAN_OR_EQUALS,

        CONTAINS,
        NOT_CONTAINS,

        MOD_K_EQUALS,
        MOD_K_NOT_EQUALS,

        PREDICATE_FUNCTION,
    }
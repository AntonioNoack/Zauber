package me.anno.zauber2.stage1

enum class ScopeType {
    COMPANION_OBJECT,
    OBJECT,

    FIELD,

    INTERFACE,
    DATA_CLASS,
    VALUE_CLASS,
    INNER_CLASS,
    ENUM_CLASS,

    INLINE_CLASS,
    LAMBDA_CLASS,

    TYPE_ALIAS
}
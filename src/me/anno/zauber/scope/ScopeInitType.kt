package me.anno.zauber.scope

enum class ScopeInitType {

    DISCOVER_MEMBERS,
    AFTER_DISCOVERY,

    RESOLVE_TYPES,

    DEFAULT_PARAMETERS,
    ADD_OVERRIDES,

    AFTER_OVERRIDES,

    RESOLVE_METHOD_BODY,

    CODE_GENERATION,
}
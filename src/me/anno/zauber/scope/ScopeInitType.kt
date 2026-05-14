package me.anno.zauber.scope

enum class ScopeInitType {

    DISCOVER_MEMBERS,
    AFTER_DISCOVERY,

    // todo in this resolveTypes step, we could resolve all method types...
    //  and when reading expressions, we can immediately read the proper types everywhere :)
    RESOLVE_TYPES,
    AFTER_RESOLVE_TYPES,

    DEFAULT_PARAMETERS,
    ADD_OVERRIDES,

    AFTER_OVERRIDES,

    RESOLVE_METHOD_BODY,

    CODE_GENERATION,
}
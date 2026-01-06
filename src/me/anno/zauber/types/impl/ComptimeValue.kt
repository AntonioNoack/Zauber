package me.anno.zauber.types.impl

import me.anno.zauber.types.Type

/**
 * This type can be used for further de-generalization.
 * */
class ComptimeValue(val type: Type, val components: List<String> /* typically just one, but we can support comptime value structs */) : Type() {
    override fun toStringImpl(depth: Int): String {
        return "Comptime[$type]($components)"
    }
}
package me.anno.generation.java

import me.anno.zauber.types.Type

object JavaBuilder {
    fun resolveType(type: Type): Type {
        var type = type
        while (true) {
            try {
                val resolved = type.resolve().specialize()
                if (resolved == type) break
                type = resolved
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }
        return type
    }
}
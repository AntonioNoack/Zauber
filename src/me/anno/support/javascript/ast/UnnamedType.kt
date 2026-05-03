package me.anno.support.javascript.ast

import me.anno.zauber.types.Type

class UnnamedType(val superType: Type?) : Type() {
    override fun toStringImpl(depth: Int): String = "{ extends $superType }"
}
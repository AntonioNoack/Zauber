package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// todo Kotlin handles types very specifically...
//  we're developing our own language, so let's do better, and handle types as first-class citizens
// todo generics could have default type "Type" :),
//  but how do we describe non-type generics??? maybe we only allow types to be implicit parameters :)

class TypePropertyTest {

    // todo only class-types have a name, but all types have a toString() method

    @Test
    fun testTypeName() {
        val code = "val tested = Int.name"
        val value = testExecute(code)
        assertEquals("Int", value.castToString())
    }
}
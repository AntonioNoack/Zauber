package me.anno.zauber.typeresolution

import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.TypeResolutionTest.Companion.testTypeResolution
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelfTypeTest {

    companion object {
        private val LOGGER = LogManager.getLogger(SelfTypeTest::class)
    }

    @Test
    fun testSelfType() {
        // this has a hacky solution, how does it know of the type being B???
        //  -> it was using the constructor parameter...
        val type0 = testTypeResolution(
            """
            open class A(val other: Self?)
            class B(other: Self?): A(other)
            val tested = B(null).other
        """.trimIndent()
        )
        check(type0 is UnionType && NullType in type0.types && type0.types.size == 2)
        val type1 = type0.types.first { it != NullType }
        LOGGER.info("Resolved Self to $type1 (should be B)")
        assertTrue(type1 is ClassType)
        assertTrue((type1 as ClassType).clazz.name == "B")
        LOGGER.info("Fields[$type1]: ${type1.clazz.fields}")
        assertFalse(type1.clazz.fields.any { it.name == "other" })
    }

    @Test
    fun testSelfType2() {
        // todo somehow the field is missing??? How???
        val type = testTypeResolution(
            """
            open class A(val other: Self?)
            class B(other: Self?): A(other)
            val tested = B(null).other!!
        """.trimIndent()
        )
        LOGGER.info("Resolved Self to $type (should be B)")
        assertTrue(type is ClassType)
        assertTrue((type as ClassType).clazz.name == "B")
    }

}
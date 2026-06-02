package me.anno.library

import me.anno.libraries.TOMLParser
import me.anno.utils.assertEquals
import org.junit.jupiter.api.Test

class TOMLParserTest {
    @Test
    fun testSimpleTOML() {
        assertEquals(
            mapOf(
                "key1" to 1L,
                "sub.key2" to 3.14,
                "sub.address" to "https://github.com/AntonioNoack/Zauber"
            ),
            TOMLParser.parseTOML(
                """
                key1 = 1
                [sub]
                key2 = 3.14
                address = "https://github.com/AntonioNoack/Zauber"
            """.trimIndent()
            )
        )
    }

    @Test
    fun testTOMLWithMap() {
        assertEquals(
            mapOf(
                "key1" to mapOf("a" to 1L, "b" to 2L)
            ),
            TOMLParser.parseTOML(
                """
                key1 = {a: 1, b: 2}
            """.trimIndent()
            )
        )
    }

    @Test
    fun testTOMLWithArray() {
        assertEquals(
            mapOf(
                "key1" to listOf(1L, 2L, 3L)
            ),
            TOMLParser.parseTOML(
                """
                key1 = [1,2,3]
            """.trimIndent()
            )
        )
    }
}
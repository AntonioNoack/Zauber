package me.anno.toml

import me.anno.libraries.TOMLParser
import org.junit.jupiter.api.Assertions.assertEquals
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
            TOMLParser.parseTOML("""
                key1 = 1
                [sub]
                key2 = 3.14
                address = "https://github.com/AntonioNoack/Zauber"
            """.trimIndent())
        )
    }
}
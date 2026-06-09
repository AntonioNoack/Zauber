package me.anno.support.cpp.preprocessor

import me.anno.utils.assertEquals
import me.anno.zauber.tokenizer.TokenList
import org.junit.jupiter.api.Test

class PreprocessorLineNumberTests : PreprocessorTestBase() {

    fun assertLines(sources: Map<String, String>, expected: List<Pair<String, String>>) {
        val tokens = preprocess(sources, "test.c")
        assertEquals(expected, groupLines(tokens.stringifyLinePositions()))
    }

    private fun TokenList.stringifyLinePositions(): List<Pair<String, String>> {
        return List(size) { i -> "${getFileName(i)}:${getLineNumber(i)}" to toString(i) }
    }

    private fun groupLines(linePositions: List<Pair<String, String>>): List<Pair<String, String>> {
        println(linePositions)
        val groups = ArrayList<Pair<String, String>>()
        for (line in linePositions) {
            if (groups.isEmpty() || groups.last().first != line.first) {
                groups.add(line)
            } else {
                val last = groups.removeLast()
                groups.add(last.first to "${last.second} ${line.second}")
            }
        }
        return groups
    }

    @Test
    fun testSimpleCFile() {
        assertLines(
            mapOf(
                "test.c" to """
            int main() {
                return 0;
            }
            """.trimIndent(),
            ),
            listOf(
                "test.c:1" to "int main ( ) {",
                "test.c:2" to "return 0 ;",
                "test.c:3" to "}",
            )
        )
    }

    @Test
    fun testInclude() {
        assertLines(
            mapOf(
                "test.c" to """
            #include <include.h>
            int main();
            """.trimIndent(),
                "include.h" to """
                    int x;
                """.trimIndent()
            ),
            listOf(
                "include.h:1" to "int x ;",
                "test.c:2" to "int main ( ) ;",
            )
        )
    }


}

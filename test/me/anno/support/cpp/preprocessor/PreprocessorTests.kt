package me.anno.support.cpp.preprocessor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreprocessorTests : PreprocessorTestBase() {

    @Test
    fun testSimpleCFile() {
        assertTokens(
            """
            int main() {
                return 0;
            }
            """,
            """
            int main ( ) { return 0 ; }
            """
        )
    }

    @Test
    fun testSimpleDefine() {
        assertTokens(
            """
        #define X 42
        int a = X;
        """,
            """
        int a = 42 ;
        """
        )
    }

    @Test
    fun testUndefinedMacro() {
        assertTokens(
            """
        int a = X;
        """,
            """
        int a = X ;
        """
        )
    }

    @Test
    fun testFunctionMacro() {
        assertTokens(
            """
        #define ADD(a,b) a + b
        int x = ADD(1,2);
        """,
            """
        int x = 1 + 2 ;
        """
        )
    }

    @Test
    fun testNestedMacros() {
        assertTokens(
            """
        #define A 1
        #define B A
        int x = B;
        """,
            """
        int x = 1 ;
        """
        )
    }

    @Test
    fun testSelfRecursiveMacro() {
        assertTokens(
            """
        #define A A
        int x = A;
        """,
            """
        int x = A ;
        """
        )
    }

    @Test
    fun testTokenPasting() {
        assertTokens(
            """
        #define JOIN(a,b) a##b
        int hello = 5;
        int x = JOIN(hel,lo);
        """,
            """
        int hello = 5 ;
        int x = hello ;
        """
        )
    }

    @Test
    fun testTokenPastingArgs() {
        assertTokens(
            """
        #define A foo
        #define B bar
        #define X(a,b) a##b
        int foobar = 1;
        int x = X(A,B);
        """,
            """
        int foobar = 1 ;
        int x = foobar ;
        """
        )
    }

    @Test
    fun testStringification() {
        assertTokens(
            """
        #define STR(x) #x
        const char* s = STR(hello + world);
        """,
            """
        const char * s = "hello + world" ;
        """
        )
    }

    @Test
    fun testStringificationNoExpansion() {
        assertTokens(
            """
        #define A 123
        #define STR(x) #x
        const char* s = STR(A);
        """,
            """
        const char * s = "A" ;
        """
        )
    }

    @Test
    fun testIfTrue() {
        assertTokens(
            """
        #if 1
        int x;
        #endif
        """,
            "int x ;"
        )
    }

    @Test
    fun testIfFalse() {
        assertTokens(
            """
        #if 0
        int x;
        #endif
        """,
            ""
        )
    }

    @Test
    fun testIfElse() {
        assertTokens(
            """
        #if 0
        int a;
        #else
        int b;
        #endif
        """,
            "int b ;"
        )
    }

    @Test
    fun testElif() {
        assertTokens(
            """
        #if 0
        int a;
        #elif 1
        int b;
        #endif
        """,
            "int b ;"
        )
    }

    @Test
    fun testIfMacroExpansion() {
        assertTokens(
            """
        #define A 1
        #if A
        int x;
        #endif
        """,
            "int x ;"
        )
    }

    @Test
    fun testIfUndefinedMacro() {
        assertTokens(
            """
        #if UNDEF
        int x;
        #endif
        """,
            ""
        )
    }

    @Test
    fun testDefined() {
        assertTokens(
            """
        #define A
        #if defined(A)
        int x;
        #endif
        """,
            "int x ;"
        )
    }

    @Test
    fun testNestedIfs() {
        assertTokens(
            """
        #if 1
          #if 0
          int a;
          #else
          int b;
          #endif
        #endif
        """,
            "int b ;"
        )
    }

    @Test
    fun testUndef() {
        assertTokens(
            """
        #define A 1
        #undef A
        int x = A;
        """,
            "int x = A ;"
        )
    }

    @Test
    fun testPragmaOnce() {
        val files = mapOf(
            "a.h" to """
            #pragma once
            int x;
        """,
            "b.c" to """
            #include "a.h"
            #include "a.h"
        """
        )

        val result = preprocess(files,"b.c")
        assertEquals(
            "int x ;",
            result.toDebugString().trim()
        )
    }

    @Test
    fun testRecursiveInclude() {
        val result = preprocess("a.h",""" #include "a.h" int x; """)
        assertTrue(result.size > 0)
    }

    @Test
    fun testLargeMacroChain() {
        val src = buildString {
            append("#define A 1\n")
            for (i in 0..100) {
                append("#define B$i A\n")
            }
            append("int x = B100;")
        }

        assertTokens(src, "int x = 1 ;")
    }

}

package me.anno.support.csharp.ast

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.support.java.ast.JavaASTBuilder
import me.anno.zauber.ast.rich.Annotation
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Types
import me.anno.zauber.utils.ResetThreadLocal.Companion.threadLocal

class CSharpASTBuilder(tokens: TokenList, root: Scope) : JavaASTBuilder(tokens, root) {
    companion object {
        private val LOGGER = LogManager.getLogger(CSharpASTBuilder::class)

        @Suppress("SpellCheckingInspection")
        val nativeCSharpTypes by threadLocal {
            Types.run {
                mapOf(
                    "sbyte" to ByteType, "SByte" to ByteType,
                    "byte" to UByteType, "Byte" to UByteType,
                    "short" to ShortType, "Short" to ShortType,
                    "ushort" to UShortType, "UShort" to UShortType,
                    "int" to IntType, "Integer" to IntType,
                    "uint" to UIntType, "UInteger" to UIntType,
                    "long" to LongType, "Long" to LongType, "nint" to LongType,
                    "ulong" to LongType, "ULong" to LongType, "nuint" to LongType,
                    "float" to FloatType, "Float" to FloatType,
                    "double" to DoubleType, "Double" to DoubleType,
                    "bool" to BooleanType, "Bool" to BooleanType,
                    "void" to UnitType, "Void" to UnitType
                )
            }
        }
    }

    override fun readFileLevel() {
        while (i < tokens.size) {
            if (LOGGER.isDebugEnabled) LOGGER.debug("readFileLevel[$i]: ${tokens.err(i)}")
            when {
                consumeIf("namespace") -> {
                    if (tokens.equals(i + 1, TokenType.OPEN_BLOCK)) {
                        // inner namespace
                        val name = consumeName(VSCodeType.NAMESPACE, VSCodeModifier.DECLARATION.flag)
                        pushBlock(ScopeType.PACKAGE, name) {
                            readFileLevel()
                        }
                    } else {
                        // file-level namespace
                        readAndApplyPackage()
                    }
                }
                consumeIf("using") -> readAndApplyImport()

                consumeIf("enum") -> readClass(ScopeType.ENUM_CLASS)
                consumeIf("class") -> readClass(ScopeType.NORMAL_CLASS)
                consumeIf("interface") -> readInterface()
                consumeIf("struct") -> {
                    addFlag(Flags.VALUE)
                    readClass(ScopeType.NORMAL_CLASS)
                }
                consumeIf("record") -> {
                    addFlag(Flags.VALUE)
                    readClass(ScopeType.NORMAL_CLASS)
                }

                consumeIf(";") -> {}// just skip it
                consumeIf("[") -> annotations.add(readAnnotation())

                tokens.equals(i, TokenType.KEYWORD) -> collectKeywords()
                tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) -> {
                    readMethodOrFieldInClass()
                }

                // todo typealias: global using BandPass = (int Min, int Max);
                // todo ""u8-suffix -> becomes ReadOnlySpan<byte>

                tokens.equals(i, "<") -> readGenericMethod()

                else -> throw NotImplementedError("Unknown token at ${tokens.err(i)}")
            }
        }
    }

    override fun readAnnotation(): Annotation {
        val i0 = --i // undo [-skip
        val path = pushArray {
            readTypePath(null)
                ?: throw IllegalStateException("Expected type path at ${tokens.err(i0)}")
        }
        return Annotation(path, emptyList())
    }


    override fun readAnnotations() {
        if (consumeIf(TokenType.OPEN_ARRAY)) {
            annotations.add(readAnnotation())
        }
    }

    override fun consumeKeyword(): Int {
        return when {
            consumeIf("public") -> Flags.PUBLIC
            consumeIf("private") -> Flags.PRIVATE
            consumeIf("internal") -> 0 // todo = not exported -> support that somehow? should exports be explicit or implicit?
            else -> super.consumeKeyword()
        }
    }


}
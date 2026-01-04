package me.anno.zauber.astbuilder

import me.anno.zauber.Compile.root
import me.anno.zauber.astbuilder.ASTBuilder.Companion.fileLevelKeywords
import me.anno.zauber.logging.LogManager
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Import
import me.anno.zauber.types.SuperCallName
import me.anno.zauber.types.Types.NullableAnyType

object ASTClassScanner {

    private val LOGGER = LogManager.getLogger(ASTClassScanner::class)

    /**
     * to make type-resolution immediately available/resolvable
     * */
    fun collectNamedClasses(tokens: TokenList) {

        var depth = 0
        var listen = -1
        var listenType = ""

        var currPackage = root
        var nextPackage = root

        val imports = ArrayList<Import>()
        val listening = ArrayList<Boolean>()
        listening.add(true)

        for (i in 0 until tokens.size) {
            when (tokens.getType(i)) {
                TokenType.OPEN_BLOCK -> {

                    if (listenType == "body?") {
                        listening.add(true)
                        currPackage = nextPackage
                    } else {
                        depth++
                        listening.add(false)
                    }

                    listen = -1
                    listenType = ""
                }
                TokenType.OPEN_CALL, TokenType.OPEN_ARRAY -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY -> depth--
                TokenType.CLOSE_BLOCK -> {
                    if (listening.removeLast()) {
                        currPackage = currPackage.parent ?: root
                    } else depth--
                }
                else -> {


                    /*if(tokens.equals(i,"Operator")) {
                        LOGGER.info("Found Operator at ${tokens.err(i)}, $depth, $listen, $listenType")
                    }*/

                    fun readClass(name: String, j0: Int) {
                        nextPackage = currPackage.getOrPut(name, null)
                        nextPackage.keywords.add(listenType)
                        nextPackage.fileName = tokens.fileName

                        // LOGGER.info("discovered $nextPackage")

                        var j = j0
                        val genericParams = if (tokens.equals(j, "<")) {
                            val genericParams = ArrayList<Parameter>()

                            j++
                            var depth = 1
                            while (depth > 0) {
                                if (depth == 1 && tokens.equals(j, TokenType.NAME) &&
                                    ((j == j0 + 1) || (tokens.equals(j - 1, ",")))
                                ) {
                                    val name = tokens.toString(j)
                                    val type = NullableAnyType
                                    genericParams.add(Parameter(name, type, nextPackage, j))
                                }

                                if (tokens.equals(j, "<")) depth++
                                else if (tokens.equals(j, ">")) depth--
                                else when (tokens.getType(j)) {
                                    TokenType.OPEN_CALL, TokenType.OPEN_ARRAY, TokenType.OPEN_BLOCK -> depth++
                                    TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY, TokenType.CLOSE_BLOCK -> depth--
                                    else -> {}
                                }
                                j++
                            }

                            genericParams
                        } else emptyList()

                        nextPackage.typeParameters = genericParams
                        nextPackage.hasTypeParameters = true
                        println("Defined type parameters for ${nextPackage.pathStr}")

                        if (tokens.equals(j, "private")) j++
                        if (tokens.equals(j, "protected")) j++
                        if (tokens.equals(j, "constructor")) j++
                        if (tokens.equals(j, TokenType.OPEN_CALL)) {
                            // skip constructor params
                            j = tokens.findBlockEnd(j, TokenType.OPEN_CALL, TokenType.CLOSE_CALL) + 1
                        }
                        if (tokens.equals(j, ":")) {
                            j++
                            while (tokens.equals(j, TokenType.NAME)) {
                                val name = tokens.toString(j++)
                                // LOGGER.info("discovered $nextPackage extends $name")
                                nextPackage.superCallNames.add(SuperCallName(name, imports))
                                if (tokens.equals(j, "<")) {
                                    j = tokens.findBlockEnd(j, "<", ">") + 1
                                }
                                if (tokens.equals(j, TokenType.OPEN_CALL)) {
                                    j = tokens.findBlockEnd(j, TokenType.OPEN_CALL, TokenType.CLOSE_CALL) + 1
                                }
                                if (tokens.equals(j, TokenType.COMMA)) j++
                                else break
                            }
                        }

                        listen = -1
                        listenType = "body?"
                    }

                    if (depth == 0) {
                        when {
                            tokens.equals(i, "package") && listening.size == 1 -> {
                                currPackage = tokens.readPath(i).first
                            }
                            tokens.equals(i, "import") && listening.size == 1 -> {
                                imports.add(tokens.readImport(i).first)
                            }

                            // tokens.equals(i, "<") -> if (listen >= 0) genericsDepth++
                            // tokens.equals(i, ">") -> if (listen >= 0) genericsDepth--

                            tokens.equals(i, "var") || tokens.equals(i, "val") || tokens.equals(i, "fun") -> {
                                listen = -1
                                listenType = ""
                            }

                            tokens.equals(i, "class") && !tokens.equals(i - 1, "::") && listening.last() -> {
                                listen = i
                                listenType = "class"
                            }
                            tokens.equals(i, "object") && listening.last() -> {
                                listen = i
                                if (listenType != "companion") listenType = "object"
                                if (listenType == "companion" && tokens.equals(i + 1, "{")) {
                                    readClass("Companion", i + 1)
                                }
                            }
                            tokens.equals(i, "companion") && listening.last() -> {
                                listen = i
                                listenType = "companion"
                            }
                            tokens.equals(i, "interface") && listening.last() -> {
                                listen = i
                                listenType = "interface"
                            }
                            tokens.equals(i, "typealias") && listening.last() -> {
                                listen = i
                                listenType = "typealias"
                            }

                            listen >= 0 && tokens.equals(i, TokenType.NAME) &&
                                    fileLevelKeywords.none { keyword -> tokens.equals(i, keyword) } -> {
                                readClass(tokens.toString(i), i + 1)
                            }
                        }
                    }
                }
            }
        }
        check(listen == -1) { "Listening for class/object/interface at ${tokens.err(listen)}" }

        // if (tokens.fileName.endsWith("Operator.kt")) throw IllegalStateException()
    }
}
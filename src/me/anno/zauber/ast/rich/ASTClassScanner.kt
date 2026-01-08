package me.anno.zauber.ast.rich

import me.anno.zauber.Compile.root
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Import
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.SuperCallName
import me.anno.zauber.types.Types.NullableAnyType

object ASTClassScanner {

    /**
     * to make type-resolution immediately available/resolvable
     * */
    fun collectNamedClasses(tokens: TokenList) {

        var depth = 0
        var hadNamedScope = false

        var currPackage = root
        var nextPackage = root

        val imports = ArrayList<Import>()
        val listening = ArrayList<Boolean>()
        listening.add(true)

        var i = 0

        fun handleBlockOpen() {
            if (hadNamedScope) {
                listening.add(true)
                currPackage = nextPackage
            } else {
                depth++
                listening.add(false)
            }
            hadNamedScope = false
        }

        fun foundNamedScope(name: String, listenType: String) {
            nextPackage = currPackage.getOrPut(name, null)
            nextPackage.keywords.add(listenType)
            nextPackage.fileName = tokens.fileName

            // LOGGER.info("discovered $nextPackage")

            var j = i
            val genericParams = if (tokens.equals(j, "<")) {
                val genericParams = ArrayList<Parameter>()

                j++
                var depth = 1
                while (depth > 0) {
                    if (depth == 1 && tokens.equals(j, TokenType.NAME) &&
                        ((j == i + 1) || (tokens.equals(j - 1, ",")))
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
            if (false) println("Defined type parameters for ${nextPackage.pathStr}")

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

            if (listenType == "enum" && tokens.equals(j, "{")) {
                hadNamedScope = true
                handleBlockOpen()
                j++

                while (j < tokens.size && !(tokens.equals(j, ";") || tokens.equals(j, "}"))) {
                    check(tokens.equals(j, TokenType.NAME)) {
                        "Expected name in enum class $currPackage, got ${tokens.err(j)}"
                    }
                    val name = tokens.toString(j++)

                    val childScope = currPackage.getOrPut(name, ScopeType.ENUM_ENTRY_CLASS)
                    childScope.hasTypeParameters = true
                    if (false) println("Defined type parameters for ${nextPackage.pathStr}.$name")

                    // skip value parameters
                    if (tokens.equals(j, "(")) {
                        j = tokens.findBlockEnd(j, "(", ")") + 1
                    }

                    // skip block body
                    if (tokens.equals(j, "{")) {
                        j = tokens.findBlockEnd(j, "{", "}") + 1
                    }

                    if (tokens.equals(j, ",")) j++
                }
                i = j // skip stuff

            } else {
                hadNamedScope = true
                i = j // skip stuff
            }
        }

        while (i < tokens.size) {
            when (tokens.getType(i)) {
                TokenType.OPEN_BLOCK -> handleBlockOpen()
                TokenType.OPEN_CALL, TokenType.OPEN_ARRAY -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_ARRAY -> depth--
                TokenType.CLOSE_BLOCK -> {
                    if (listening.removeLast()) {
                        currPackage = currPackage.parent ?: root
                    } else depth--
                }
                else -> {

                    if (depth == 0) {
                        when {
                            tokens.equals(i, "package") && listening.size == 1 -> {
                                val (path, ni) = tokens.readPath(i)
                                currPackage = path
                                i = ni
                                continue // without i++
                            }
                            tokens.equals(i, "import") && listening.size == 1 -> {
                                val (path, ni) = tokens.readImport(i)
                                imports.add(path)
                                i = ni
                                continue // without i++
                            }

                            // tokens.equals(i, "<") -> if (listen >= 0) genericsDepth++
                            // tokens.equals(i, ">") -> if (listen >= 0) genericsDepth--

                            tokens.equals(i, "var") || tokens.equals(i, "val") || tokens.equals(i, "fun") -> {
                                hadNamedScope = false
                            }

                            tokens.equals(i, "class") && tokens.equals(i - 1, "enum") && listening.last() -> {
                                check(tokens.equals(++i, TokenType.NAME))
                                val name = tokens.toString(i++)
                                foundNamedScope(name, "enum")
                                continue // without i++
                            }

                            tokens.equals(i, "class") && !tokens.equals(i - 1, "::") && listening.last() -> {
                                check(tokens.equals(++i, TokenType.NAME))
                                val name = tokens.toString(i++)
                                foundNamedScope(name, "class")
                                continue // without i++
                            }

                            tokens.equals(i, "object") && !tokens.equals(i - 1, "companion")
                                    && !tokens.equals(i + 1, ":") && listening.last() -> {
                                check(tokens.equals(++i, TokenType.NAME)) {
                                    "Expected name for object, but got ${tokens.err(i)}"
                                }
                                val name = tokens.toString(i++)
                                foundNamedScope(name, "object")
                                continue // without i++
                            }
                            tokens.equals(i, "companion") && listening.last() -> {
                                i++ // skip companion
                                check(tokens.equals(i++, "object"))
                                val name = if (tokens.equals(i, TokenType.NAME)) {
                                    tokens.toString(i++)
                                } else "Companion"
                                foundNamedScope(name, "companion")
                                continue // without i++
                            }
                            tokens.equals(i, "interface") && listening.last() -> {
                                check(tokens.equals(++i, TokenType.NAME))
                                val name = tokens.toString(i++)
                                foundNamedScope(name, "interface")
                                continue // without i++
                            }
                            tokens.equals(i, "typealias") && listening.last() -> {
                                check(tokens.equals(++i, TokenType.NAME))
                                val name = tokens.toString(i++)
                                foundNamedScope(name, "typealias")
                                continue // without i++
                            }
                        }
                    }
                }
            }
            i++
        }
    }
}
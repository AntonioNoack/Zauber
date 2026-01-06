package me.anno.c.preprocessor

import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType

class Preprocessor(
    val files: Map<String, TokenList>,
    val includeResolver: (currentFile: String, include: String) -> String?, // (currentFile, include) -> filename
) {

    companion object {
        private const val MAX_EXPANSION_DEPTH = 64
    }

    private val macros = HashMap<String, Macro>()
    private val pragmaOnceFiles = HashSet<String>()

    fun preprocess(fileName: String): TokenList {
        val input = files[fileName] ?: error("File not found: $fileName")
        val output = TokenBuilder(fileName)

        var i = 0
        while (i < input.size) {
            i = if (isDirective(input, i)) {
                handleDirective(input, i, output)
            } else {
                expandOrCopy(input, i, output, 0)
            }
        }

        return output.tokens
    }

    private fun isDirective(tokens: TokenList, i: Int): Boolean {
        return tokens.equals(i, "#") &&
                (i == 0 || !tokens.isSameLine(i - 1, i))
    }

    private fun handleDirective(tokens: TokenList, i0: Int, out: TokenBuilder): Int {
        var i = i0 + 1
        check(tokens.equals(i, TokenType.NAME) || tokens.equals(i, TokenType.KEYWORD)) {
            "Expected directive name, but got ${tokens.err(i)}"
        }
        when (tokens.toString(i++)) {
            "define" -> i = handleDefine(tokens, i)
            "undef" -> macros.remove(tokens.toString(i++))
            "include" -> handleInclude(tokens, i, out)
            "ifdef" -> i = handleIfDefined(tokens, i, true, out)
            "ifndef" -> i = handleIfDefined(tokens, i, false, out)
            "if" -> i = handleIfExpression(tokens, i, out)
            "elif", "else", "endif" -> error("Unexpected directive ${tokens.err(i - 1)}")
            "pragma" -> {
                if (tokens.toString(i) == "once") {
                    pragmaOnceFiles += tokens.fileName
                }
            }
        }
        return skipToLineEnd(tokens, i)
    }

    private fun handleDefine(tokens: TokenList, i0: Int): Int {
        var i = i0
        val name = tokens.toString(i++)
        if (tokens.equals(i, TokenType.OPEN_CALL)) {
            // function-like macro
            val params = ArrayList<String>()
            i++ // (
            while (i < tokens.size && !tokens.equals(i, TokenType.CLOSE_CALL)) {
                params.add(tokens.toString(i++))
                if (tokens.equals(i, ",")) i++
            }
            i++ // )
            val body = collectRestOfLine(tokens, i)
            macros[name] = FunctionMacro(name, params, tokens, body)
        } else {
            val body = collectRestOfLine(tokens, i)
            macros[name] = ObjectMacro(name, tokens, body)
        }
        return i
    }

    private fun handleInclude(tokens: TokenList, i: Int, out: TokenBuilder) {
        val raw = tokens.toString(i)
        val path = raw.trim('"', '<', '>')
        val resolved = includeResolver(tokens.fileName, path)
            ?: error("Include not found: $path")

        if (resolved in pragmaOnceFiles) return

        val included = preprocess(resolved)
        for (j in 0 until included.size) {
            out.addRange(
                included.getType(j),
                included.getI0(j),
                included.getI1(j),
                included
            )
        }
    }

    private fun expandOrCopy(
        tokens: TokenList,
        i: Int,
        out: TokenBuilder,
        depth: Int
    ): Int {
        if (depth > MAX_EXPANSION_DEPTH) {
            out.addToken(tokens, i)
            return i + 1
        }

        if (tokens.getType(i) == TokenType.NAME) {
            val name = tokens.toString(i)
            val macro = macros[name]
            if (macro != null) {
                return expandMacro(tokens, i, macro, out, depth + 1)
            }
        }

        out.addToken(tokens, i)
        return i + 1
    }

    private fun expandMacro(
        tokens: TokenList,
        i0: Int,
        macro: Macro,
        out: TokenBuilder,
        depth: Int
    ): Int {
        return when (macro) {
            is ObjectMacro -> {
                val tokens = macro.tokens
                for (index in macro.body) {
                    expandOrCopy(tokens, index, out, depth)
                }
                i0 + 1
            }
            is FunctionMacro -> {
                var i = i0 + 1
                if (!tokens.equals(i, TokenType.OPEN_CALL)) {
                    out.addToken(tokens, i0)
                    return i0 + 1
                }

                val args = readMacroArgs(tokens, ++i)
                i = tokens.findBlockEnd(i - 1, TokenType.OPEN_CALL, TokenType.CLOSE_CALL) + 1

                macro.body.forEachIndexed { idx, bodyIndex ->
                    if (macro.tokens.equals(bodyIndex, "##")) {
                        val prev = macro.body.first + idx - 1
                        val next = macro.body.first + idx + 1

                        val left = substituteOrToken(prev, args, macro, macro.tokens)
                        val right = substituteOrToken(next, args, macro, macro.tokens)

                        pasteTokens(tokens, left, right, out)
                    } else if (macro.tokens.equals(bodyIndex, "#")) {
                        val param = macro.tokens.toString(macro.body.first + idx + 1)
                        val argIndex = macro.params.indexOf(param)
                        if (argIndex >= 0) {
                            stringifyArg(tokens, args[argIndex], out)
                        }
                    } else {
                        expandOrCopy(tokens, bodyIndex, out, depth)
                    }
                }
                i
            }
        }
    }

    private fun stringifyArg(
        tokens: TokenList,
        arg: List<Int>,
        out: TokenBuilder
    ) {
        val start = tokens.getI0(arg.first())
        val end = tokens.getI1(arg.last())
        out.addRange(TokenType.STRING, start, end, tokens)
    }

    private fun readMacroArgs(tokens: TokenList, i0: Int): List<List<Int>> {
        val args = ArrayList<ArrayList<Int>>()
        var depth = 0
        var current = ArrayList<Int>()
        var i = i0

        while (true) {
            when {
                tokens.equals(i, TokenType.OPEN_CALL) -> depth++
                tokens.equals(i, TokenType.CLOSE_CALL) && depth-- == 0 -> {
                    args.add(current)
                    return args
                }
                tokens.equals(i, ",") && depth == 0 -> {
                    args.add(current)
                    current = ArrayList()
                }
                else -> current.add(i)
            }
            i++
        }
    }

    private fun collectRestOfLine(tokens: TokenList, i0: Int): IntRange {
        var i = i0
        while (i < tokens.size && tokens.isSameLine(i0 - 1, i)) {
            i++
        }
        return i0 until i
    }

    private fun skipToLineEnd(tokens: TokenList, i: Int): Int {
        var j = i
        while (j < tokens.size && tokens.isSameLine(i, j)) j++
        return j
    }

    private fun handleIfDefined(
        tokens: TokenList,
        i0: Int,
        positive: Boolean,
        out: TokenBuilder
    ): Int {
        val name = tokens.toString(i0)
        val cond = { macros.containsKey(name) == positive }
        return handleIfCommon(tokens, i0, out, cond)
    }

    private fun handleIfExpression(
        tokens: TokenList,
        i0: Int,
        out: TokenBuilder
    ): Int {
        val exprTokens = collectLine(tokens, i0)
        val cond = { evalIfExpression(tokens, exprTokens) }
        return handleIfCommon(tokens, exprTokens.last, out, cond)
    }

    private fun handleIfCommon(
        tokens: TokenList,
        i0: Int,
        out: TokenBuilder,
        firstCond: () -> Boolean
    ): Int {

        val branches = ArrayList<IfBranch>()
        var i: Int
        var activeCond = firstCond

        var blockStart = skipLine(tokens, i0)
        println("Skipped first line from $i0 to $blockStart")

        loop@ while (true) {
            val blockEnd = findNextDirective(tokens, blockStart)
            println("Block start/end: $blockStart .. $blockEnd")
            branches += IfBranch(activeCond, blockStart, blockEnd)

            i = blockEnd
            if (!tokens.equals(i, "#")) break
            i++

            when (tokens.toString(i++)) {
                "elif" -> {
                    val expr = collectLine(tokens, i)
                    activeCond = { evalIfExpression(tokens, expr) }
                    blockStart = skipLine(tokens, i + expr.last + 1 - expr.first)
                    println("Skipped line from $i to $blockStart")
                }
                "else" -> {
                    activeCond = { true }
                    blockStart = skipLine(tokens, i - 1)
                    println("Skipped line from $i to $blockStart")
                }
                "endif" -> {
                    i = skipLine(tokens, i - 1)
                    break@loop
                }
                else -> error("Invalid directive inside #if: ${tokens.err(i - 1)}")
            }
        }

        // emit first matching branch
        println("Checking ${branches.size} branches")
        for (b in branches) {
            if (b.shouldEmit()) {
                println("Branch $b returned true")
                emitRange(tokens, b.start, b.end, out)
                break
            } else {
                println("Branch $b returned false")
            }
        }

        return i
    }

    private fun evalIfExpression(
        tokens: TokenList,
        expr: IntRange
    ): Boolean {

        // Step 1: expand macros
        val expanded0 = TokenBuilder(tokens.fileName)
        var i = expr.first
        while (i <= expr.last) {
            i = expandOrCopy(tokens, i, expanded0, 0)
        }

        val expanded = expanded0.tokens

        // Step 2: normalize tokens
        val values = ArrayList<Any>()
        i = 0
        while (i < expanded.size) {
            when {
                expanded.equals(i, "defined") -> {
                    val name = expanded.toString(i + 2)
                    values += macros.containsKey(name)
                    i += 4
                }
                expanded.getType(i) == TokenType.NUMBER ->
                    values += expanded.toString(i).toLong()
                expanded.getType(i) == TokenType.NAME ->
                    values += 0L // undefined identifiers -> 0
                else ->
                    values += expanded.toString(i)
            }
            i++
        }

        return evalBooleanExpr(values)
    }

    private fun evalBooleanExpr(values: List<Any>): Boolean {
        return BooleanExprEval(values).eval()
    }

    private class BooleanExprEval(val tokens: List<Any>) {

        fun eval(): Boolean {
            return parseOr()
        }

        var i = 0

        fun parsePrimary(): Boolean {
            return when (val v = tokens[i++]) {
                is Boolean -> v
                is Long -> v != 0L
                "(" -> {
                    val r = parseOr()
                    i++ // )
                    r
                }
                "!" -> !parsePrimary()
                else -> error("Invalid #if token $v")
            }
        }

        fun parseCmp(): Boolean {
            val left = parsePrimary()
            if (i < tokens.size && tokens[i] is String) {
                val op = tokens[i] as String
                if (op in listOf("==", "!=", "<", "<=", ">", ">=")) {
                    i++
                    val right = parsePrimary()
                    return when (op) {
                        "==" -> left == right
                        "!=" -> left != right
                        "<" -> !left && right
                        "<=" -> !left || right
                        ">" -> left && !right
                        ">=" -> left || !right
                        else -> false
                    }
                }
            }
            return left
        }

        fun parseAnd(): Boolean {
            var v = parseCmp()
            while (i < tokens.size && tokens[i] == "&&") {
                i++
                v = v && parseCmp()
            }
            return v
        }

        fun parseOr(): Boolean {
            var v = parseAnd()
            while (i < tokens.size && tokens[i] == "||") {
                i++
                v = v || parseAnd()
            }
            return v
        }
    }

    private fun collectLine(tokens: TokenList, i0: Int): IntRange {
        var i = i0
        while (i < tokens.size && tokens.isSameLine(i0 - 1, i)) {
            i++
        }
        return i0 until i
    }

    private fun skipLine(tokens: TokenList, i: Int): Int {
        var j = i
        while (j < tokens.size && tokens.isSameLine(i, j)) j++
        return j
    }

    private fun findNextDirective(tokens: TokenList, i0: Int): Int {
        var i = i0
        while (i < tokens.size) {
            if (tokens.equals(i, "#") && !tokens.isSameLine(i - 1, i)) return i
            i++
        }
        return tokens.size
    }

    private fun emitRange(src: TokenList, i0: Int, i1: Int, dst: TokenBuilder) {
        for (i in i0 until i1) {
            dst.addToken(src, i)
        }
    }

    private fun pasteTokens(tokens: TokenList, left: Int, right: Int, out: TokenBuilder) {
        val i0 = tokens.getI0(left)
        val i1 = tokens.getI1(right)
        out.addRange(TokenType.NAME, i0, i1, tokens)
    }

    private fun substituteOrToken(
        index: Int,
        args: List<List<Int>>,
        macro: FunctionMacro,
        tokens: TokenList
    ): Int {
        val name = tokens.toString(index)
        val argIndex = macro.params.indexOf(name)
        return if (argIndex >= 0) args[argIndex].first() else index
    }
}







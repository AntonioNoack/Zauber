package me.anno.c.preprocessor

import me.anno.zauber.tokenizer.TokenList

class FunctionMacro(
    override val name: String,
    val params: List<String>,
    val tokens: TokenList,
    val body: IntRange
) : Macro()
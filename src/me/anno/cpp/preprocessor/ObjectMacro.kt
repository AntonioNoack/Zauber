package me.anno.cpp.preprocessor

import me.anno.zauber.tokenizer.TokenList

class ObjectMacro(
    override val name: String,
    val tokens: TokenList,
    val body: IntRange // token indices in defining TokenList
) : Macro()
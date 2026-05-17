package me.anno.zauber.ast.rich.parser

import me.anno.zauber.tokenizer.TokenType

fun ZauberASTBuilderBase.readWhereConditions(): List<WhereTypeCondition> {
    return if (consumeIf("where")) {
        val conditions = ArrayList<WhereTypeCondition>()
        while (true) {

            check(tokens.equals(i, TokenType.NAME))
            check(tokens.equals(i + 1, ":"))

            val name = tokens.toString(i++)
            consume(TokenType.COMMA)
            val type = readTypeNotNull(null, true)
            conditions.add(WhereTypeCondition(name, type))

            if (tokens.equals(i, ",") &&
                tokens.equals(i + 1, TokenType.NAME) &&
                tokens.equals(i + 2, ":")
            ) {
                // skip comma and continue reading conditions
                consume(TokenType.COMMA)
            } else {
                // done
                break
            }
        }
        conditions
    } else emptyList()
}

package me.anno.support.cpp.ast.rich

import me.anno.zauber.ast.rich.parser.Associativity
import me.anno.zauber.ast.rich.parser.Operator


val cOperators = mapOf(

    // comma
    "," to Operator(",", 1, Associativity.LEFT),

    // assignments
    "=" to Operator("=", 2, Associativity.RIGHT),
    "+=" to Operator("+=", 2, Associativity.RIGHT),
    "-=" to Operator("-=", 2, Associativity.RIGHT),
    "*=" to Operator("*=", 2, Associativity.RIGHT),
    "/=" to Operator("/=", 2, Associativity.RIGHT),
    "%=" to Operator("%=", 2, Associativity.RIGHT),
    "&=" to Operator("&=", 2, Associativity.RIGHT),
    "|=" to Operator("|=", 2, Associativity.RIGHT),
    "^=" to Operator("^=", 2, Associativity.RIGHT),
    "<<=" to Operator("<<=", 2, Associativity.RIGHT),
    ">>=" to Operator(">>=", 2, Associativity.RIGHT),

    // conditional
    "?:" to Operator("?:", 3, Associativity.RIGHT),

    // logical
    "||" to Operator("||", 4, Associativity.LEFT),
    "&&" to Operator("&&", 5, Associativity.LEFT),

    // bitwise
    "|" to Operator("|", 6, Associativity.LEFT),
    "^" to Operator("^", 7, Associativity.LEFT),
    "&" to Operator("&", 8, Associativity.LEFT),

    // equality
    "==" to Operator("==", 9, Associativity.LEFT),
    "!=" to Operator("!=", 9, Associativity.LEFT),

    // relational
    "<" to Operator("<", 10, Associativity.LEFT),
    ">" to Operator(">", 10, Associativity.LEFT),
    "<=" to Operator("<=", 10, Associativity.LEFT),
    ">=" to Operator(">=", 10, Associativity.LEFT),

    // shift
    "<<" to Operator("<<", 11, Associativity.LEFT),
    ">>" to Operator(">>", 11, Associativity.LEFT),

    // additive
    "+" to Operator("+", 12, Associativity.LEFT),
    "-" to Operator("-", 12, Associativity.LEFT),

    // multiplicative
    "*" to Operator("*", 13, Associativity.LEFT),
    "/" to Operator("/", 13, Associativity.LEFT),
    "%" to Operator("%", 13, Associativity.LEFT),

    // member / scope
    "." to Operator(".", 15, Associativity.LEFT),
    "->" to Operator("->", 15, Associativity.LEFT),
    "::" to Operator("::", 15, Associativity.LEFT),
)
package me.anno.cpp.ast.rich

import me.anno.zauber.ast.rich.Assoc
import me.anno.zauber.ast.rich.Operator


val cOperators = mapOf(

    // comma
    "," to Operator(",", 1, Assoc.LEFT),

    // assignments
    "=" to Operator("=", 2, Assoc.RIGHT),
    "+=" to Operator("+=", 2, Assoc.RIGHT),
    "-=" to Operator("-=", 2, Assoc.RIGHT),
    "*=" to Operator("*=", 2, Assoc.RIGHT),
    "/=" to Operator("/=", 2, Assoc.RIGHT),
    "%=" to Operator("%=", 2, Assoc.RIGHT),
    "&=" to Operator("&=", 2, Assoc.RIGHT),
    "|=" to Operator("|=", 2, Assoc.RIGHT),
    "^=" to Operator("^=", 2, Assoc.RIGHT),
    "<<=" to Operator("<<=", 2, Assoc.RIGHT),
    ">>=" to Operator(">>=", 2, Assoc.RIGHT),

    // conditional
    "?:" to Operator("?:", 3, Assoc.RIGHT),

    // logical
    "||" to Operator("||", 4, Assoc.LEFT),
    "&&" to Operator("&&", 5, Assoc.LEFT),

    // bitwise
    "|" to Operator("|", 6, Assoc.LEFT),
    "^" to Operator("^", 7, Assoc.LEFT),
    "&" to Operator("&", 8, Assoc.LEFT),

    // equality
    "==" to Operator("==", 9, Assoc.LEFT),
    "!=" to Operator("!=", 9, Assoc.LEFT),

    // relational
    "<" to Operator("<", 10, Assoc.LEFT),
    ">" to Operator(">", 10, Assoc.LEFT),
    "<=" to Operator("<=", 10, Assoc.LEFT),
    ">=" to Operator(">=", 10, Assoc.LEFT),

    // shift
    "<<" to Operator("<<", 11, Assoc.LEFT),
    ">>" to Operator(">>", 11, Assoc.LEFT),

    // additive
    "+" to Operator("+", 12, Assoc.LEFT),
    "-" to Operator("-", 12, Assoc.LEFT),

    // multiplicative
    "*" to Operator("*", 13, Assoc.LEFT),
    "/" to Operator("/", 13, Assoc.LEFT),
    "%" to Operator("%", 13, Assoc.LEFT),

    // member / scope
    "." to Operator(".", 15, Assoc.LEFT),
    "->" to Operator("->", 15, Assoc.LEFT),
    "::" to Operator("::", 15, Assoc.LEFT),
)
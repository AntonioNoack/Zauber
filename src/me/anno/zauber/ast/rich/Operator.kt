package me.anno.zauber.ast.rich

enum class Assoc { LEFT, RIGHT }

data class Operator(val symbol: String, val precedence: Int, val assoc: Assoc)

val operators = mapOf(

    // assignments
    "=" to Operator("=", 1, Assoc.RIGHT),
    "+=" to Operator("+=", 1, Assoc.RIGHT),
    "-=" to Operator("-=", 1, Assoc.RIGHT),
    "*=" to Operator("*=", 1, Assoc.RIGHT),
    "/=" to Operator("/=", 1, Assoc.RIGHT),
    "%=" to Operator("%=", 1, Assoc.RIGHT),

    "?:" to Operator("?:", 2, Assoc.LEFT),

    // logical
    "||" to Operator("||", 3, Assoc.LEFT),
    "&&" to Operator("&&", 4, Assoc.LEFT),

    // | = 5, ^ = 6, & = 7

    // comparing
    "===" to Operator("===", 8, Assoc.LEFT),
    "!==" to Operator("!==", 8, Assoc.LEFT),
    "==" to Operator("==", 8, Assoc.LEFT),
    "!=" to Operator("!=", 8, Assoc.LEFT),

    "<" to Operator("<", 9, Assoc.LEFT),
    ">" to Operator(">", 9, Assoc.LEFT),
    "<=" to Operator("<=", 9, Assoc.LEFT),
    ">=" to Operator(">=", 9, Assoc.LEFT),

    // these look like infix, but are special
    "in" to Operator("in", 10, Assoc.LEFT),
    "!in" to Operator("!in", 10, Assoc.LEFT),
    "is" to Operator("is", 10, Assoc.LEFT),
    "!is" to Operator("!is", 10, Assoc.LEFT),

    // infix
    "shl" to Operator("shl", 11, Assoc.LEFT),
    "shr" to Operator("shr", 11, Assoc.LEFT),
    "ushr" to Operator("ushr", 11, Assoc.LEFT),
    "and" to Operator("and", 11, Assoc.LEFT),
    "or" to Operator("or", 11, Assoc.LEFT),
    "xor" to Operator("xor", 11, Assoc.LEFT),
    "to" to Operator("to", 11, Assoc.LEFT),
    "step" to Operator("step", 11, Assoc.LEFT),
    "until" to Operator("until", 11, Assoc.LEFT),
    "downTo" to Operator("downTo", 11, Assoc.LEFT),
    ".." to Operator("..", 11, Assoc.LEFT),

    // these look like infix, but are special
    "as" to Operator("as", 12, Assoc.LEFT),
    "as?" to Operator("as?", 12, Assoc.LEFT),

    // <<, >> is somewhere between +/- and >/</>=/<=

    // maths
    "+" to Operator("+", 15, Assoc.LEFT),
    "-" to Operator("-", 15, Assoc.LEFT),

    "*" to Operator("*", 20, Assoc.LEFT),
    "/" to Operator("/", 20, Assoc.LEFT),
    "%" to Operator("%", 20, Assoc.LEFT),

    "?." to Operator(".?", 30, Assoc.LEFT),
    "." to Operator(".", 30, Assoc.LEFT),
    "::" to Operator("::", 30, Assoc.LEFT),
)

val dotOperator = operators["."]!!
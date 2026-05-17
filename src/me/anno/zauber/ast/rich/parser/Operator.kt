package me.anno.zauber.ast.rich.parser

data class Operator(val symbol: String, val precedence: Int, val associativity: Associativity)

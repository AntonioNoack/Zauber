package me.anno.zauber.ast.simple

class SimpleGraph {
    val entry = SimpleBlock()
    val blocks = ArrayList<SimpleBlock>()

    init {
        blocks.add(entry)
    }
}
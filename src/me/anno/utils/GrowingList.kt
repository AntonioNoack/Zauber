package me.anno.utils

class GrowingList<V>(val generate: (Int) -> V) {

    private val elements = ArrayList<V>()

    operator fun get(index: Int): V {
        while (index >= elements.size) {
            elements.add(generate(elements.size))
        }
        return elements[index]
    }

    val size get() = elements.size

}
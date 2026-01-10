package zauber

/**
 * In most cases, you should not use this class.
 * Use an Array, an ArrayList or List(size){} or .map{} instead, please.
 * */
class LinkedList<V>(capacity: Int = 16) : MutableList<V> {

    private val content = Array<V>(capacity)
    private val previous = Array<Int>(capacity)
    private val next = Array<Int>(capacity)

    var head = -1
    var tail = -1

    override fun get(index: Int): V {
        return content[getStorageIndex(index)]
    }

    private fun getStorageIndex(index: Int): Int {
        if (index < 0) return -1
        var currIndex = head
        repeat(index) {
            currIndex = next[currIndex]
        }
        return currIndex
    }

    override fun isEmpty(): Boolean = (head == -1)

    override fun listIterator(startIndex: Int): ListIterator<V> {
        return object : ListIterator<V> {
            var externalIndex = startIndex
            var nextIndex = getStorageIndex(externalIndex)
            var prevIndex = getStorageIndex(externalIndex - 1)
            override fun hasNext(): Boolean = nextIndex >= 0
            override fun next(): V {
                prevIndex = nextIndex
                nextIndex = next[nextIndex]
                externalIndex++
                return content[prevIndex]
            }

            override fun hasPrevious(): Boolean = prevIndex >= 0
            override fun previous(): V {
                nextIndex = prevIndex
                prevIndex = previous[prevIndex]
                externalIndex--
                return content[nextIndex] // todo this this correct?
            }

            override fun nextIndex(): Int = externalIndex
            override fun previousIndex(): Int = externalIndex - 1
        }
    }

    override var size: Int
        private set

    override fun indexOf(element: V): Int {
        var currIndex = head
        var result = 0
        while (currIndex != -1) {
            if (content[currIndex] == element) {
                return result
            }
            currIndex = next[currIndex]
            result++
        }
        return -1
    }

    override fun lastIndexOf(element: V): Int {
        var currIndex = tail
        var result = size - 1
        while (currIndex != -1) {
            if (content[currIndex] == element) {
                return result
            }
            currIndex = previous[currIndex]
            result--
        }
        return -1
    }

    override fun set(index: Int, value: V): V {
        TODO("Not yet implemented")
    }

    override fun add(element: V): Boolean {
        TODO("Not yet implemented")
    }

    override fun add(index: Int, element: V): Boolean {
        TODO("Not yet implemented")
    }

    override fun addAll(elements: Collection<V>): Boolean {
        TODO("Not yet implemented")
    }

    override fun remove(element: V): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(elements: Collection<V>): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAt(index: Int): V {
        TODO("Not yet implemented")
    }

    override fun removeFirst(): V {
        TODO("Not yet implemented")
    }

    override fun removeLast(): V {
        TODO("Not yet implemented")
    }

    override fun retainAll(elements: Collection<V>): Boolean {
        TODO("Not yet implemented")
    }

}
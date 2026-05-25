package zauber.inheritance

@Static
private external fun readFromClassTable(addr: Int): Int

@Static
private external fun readFromInterfaceTable(addr: Int): Int

@Static
@Suppress("unused")
fun resolveClassCall(classIndex: Int, methodIndex: Int): Int {
    val offset = readFromClassTable(classIndex)
    return readFromClassTable(offset + methodIndex)
}

@Static
@Suppress("unused")
fun resolveInterfaceCall(classIndex: Int, methodIndex: Int): Int {
    var i0 = readFromInterfaceTable(classIndex) * 2
    val i1 = readFromInterfaceTable(classIndex + 1) * 2
    while (i0 < i1) {
        val givenMethodIndex = readFromInterfaceTable(i0)
        if (givenMethodIndex == methodIndex) {
            return readFromInterfaceTable(i0 + 1)
        }
        i0 += 2
    }
    // todo fail somehow
    return -1
}
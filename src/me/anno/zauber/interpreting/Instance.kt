package me.anno.zauber.interpreting

class Instance(
    val type: ZClass,
    val properties: Array<Instance?>,
    val id: Int
) {

    var rawValue: Any? = null

    override fun toString(): String {
        return if (rawValue == null) {
            "Instance@$id($type,${properties.toList()})"
        } else {
            "Instance@$id($type,${properties.toList()},$rawValue)"
        }
    }
}
package me.anno.zauber.interpreting

class Instance(val type: ZClass, val properties: Array<Instance?>) {
    var rawValue: Any? = null

    override fun toString(): String {
        return "Instance($type,${properties.toList()})"
    }
}
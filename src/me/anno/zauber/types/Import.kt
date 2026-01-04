package me.anno.zauber.types

class Import(val path: Scope, val allChildren: Boolean, val name: String) {
    override fun toString(): String {
        return if(allChildren) "Import(${path.pathStr}.*)"
        else "Import(${path.pathStr} as '$name')"
    }
}
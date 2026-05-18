package me.anno.libraries

import java.net.URI

class Library {
    var name = ""
    var version = "0.0.1"
    var source: URI? = null

    val dependencies = ArrayList<Library>()

    override fun toString(): String {
        return "Library(name='$name', version='$version', source=$source, dependencies=$dependencies)"
    }
}
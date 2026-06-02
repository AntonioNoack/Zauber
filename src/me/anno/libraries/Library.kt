package me.anno.libraries

import me.anno.support.Language
import java.net.URI

class Library {

    var name = ""
    var version = "0.0.1"
    var source: URI? = null

    // todo paths in that library are rewritten in the following way:
    var newPackage = ""
    var oldPackage = ""

    var language = Language.ZAUBER

    val dependencies = ArrayList<Library>()

    override fun toString(): String {
        return "Library(name='$name', version='$version', source=$source, dependencies=$dependencies, " +
                "newPackage='$newPackage', oldPackage='$oldPackage', language=$language)"
    }

    fun loadIntoScope() {
        TODO("Load package into scope...")
    }

}
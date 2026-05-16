package me.anno.zauber.expansion

import me.anno.zauber.types.Specialization

class DependencyData {
    // createdClasses = ClassType seems good enough, but actually, we need ClassSpecialization for inner classes...
    val createdClasses = HashSet<Specialization>()
    val calledMethods = HashSet<Specialization>()

    // if we make/give fields a scope, we could use specialization here...
    //   but we still have code, that moves fields, so...
    val getFields = HashSet<Specialization>()
    val setFields = HashSet<Specialization>()
}
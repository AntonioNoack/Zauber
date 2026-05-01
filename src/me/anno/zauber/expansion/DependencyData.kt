package me.anno.zauber.expansion

import me.anno.zauber.types.specialization.ClassSpecialization
import me.anno.zauber.types.specialization.FieldSpecialization
import me.anno.zauber.types.specialization.MethodSpecialization

class DependencyData {
    // createdClasses = ClassType seems good enough, but actually, we need ClassSpecialization for inner classes...
    val createdClasses = HashSet<ClassSpecialization>()
    val calledMethods = HashSet<MethodSpecialization>()
    val getFields = HashSet<FieldSpecialization>()
    val setFields = HashSet<FieldSpecialization>()
}
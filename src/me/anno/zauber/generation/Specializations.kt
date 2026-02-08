package me.anno.zauber.generation

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.types.Scope
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization
import me.anno.zauber.types.specialization.TypeSpecialization

object Specializations {

    val specialization get() = specializations.last()
    val specializations = ArrayList<Specialization>().apply {
        add(noSpecialization)
    }

    val todoTypeSpecializations = HashSet<TypeSpecialization>()
    val todoMethodSpecializations = HashSet<MethodSpecialization>()

    val doneTypeSpecializations = HashSet<TypeSpecialization>()
    val doneMethodSpecializations = HashSet<MethodSpecialization>()

    fun foundTypeSpecialization(type: Scope, specialization: Specialization) {
        // todo if inside the current scope, we must extend the specialization,
        //  aka concat them
        val spec = TypeSpecialization(type, specialization)
        if (doneTypeSpecializations.add(spec)) {
            todoTypeSpecializations.add(spec)
        }
    }

    fun foundMethodSpecialization(type: MethodLike, specialization: Specialization) {
        val spec = MethodSpecialization(type, specialization)
        if (doneMethodSpecializations.add(spec)) {
            todoMethodSpecializations.add(spec)
        }
    }

    fun generate(
        handleType: (TypeSpecialization) -> Unit,
        handleMethod: (MethodSpecialization) -> Unit
    ) {
        // todo what is a reasonable limit?
        //  do we need a configurable limit?
        val limit = 10
        for (i in 0 until limit) {
            println("Specializations ${i + 1}/$limit: ${todoTypeSpecializations.size} + ${todoMethodSpecializations.size}")
            if (todoTypeSpecializations.isNotEmpty()) {
                val list = ArrayList(todoTypeSpecializations)
                todoTypeSpecializations.clear()
                for (spec in list) handleType(spec)
            }

            if (todoMethodSpecializations.isNotEmpty()) {
                val list = ArrayList(todoMethodSpecializations)
                todoMethodSpecializations.clear()
                for (spec in list) handleMethod(spec)
            }

            if (todoTypeSpecializations.isEmpty() && todoMethodSpecializations.isEmpty()) {
                return
            }
        }
    }
}
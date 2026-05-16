package me.anno.generation

import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Specialization.Companion.noSpecialization

object Specializations {

    val specialization get() = specializations.last()
    val specializations = ArrayList<Specialization>().apply {
        add(noSpecialization)
    }

    fun reset() {
        specializations.clear()
        specializations.add(noSpecialization)
    }
}
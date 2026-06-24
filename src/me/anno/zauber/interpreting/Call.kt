package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.fields.LocalField
import me.anno.zauber.ast.simple.fields.SimpleField

// todo create a pool of these
class Call private constructor(var method: MethodLike) {

    companion object {
        private val pool = ThreadLocal.withInitial { ArrayList<Call>() }

        fun create(method: MethodLike): Call {
            val pool = pool.get()
            val call = pool.removeLastOrNull() ?: Call(method)
            call.method = method
            return call
        }
    }

    fun recycle() {
        pool.get().add(this)
    }

    // we know how many there are, so we could replace this with an array
    //  yes, but then we couldn't reuse this class for later calls

    val simpleFields = ArrayList<Instance?>()
    val localFields = ArrayList<Instance?>()

    fun setLocal(field: LocalField, instance: Instance) {
        localFields[field.id] = instance
    }

    fun setSimple(field: SimpleField, instance: Instance) {
        // todo we use SimpleField for input variables and renaming:
        //  when we do that, we must mark it as non-mergeable, because each field can be merged only once
        check(field.dst == field)
        simpleFields[field.dst.id] = instance
    }

    lateinit var graph: SimpleGraph
}

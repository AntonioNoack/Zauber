package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization

class SimpleSelfConstructor(
    unusedDst: SimpleField,
    val isThis: Boolean,
    val self: SimpleField,
    val method: Constructor,
    specialization: Specialization,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleCallable(unusedDst, specialization, scope, origin) {

    init {
        check(method.valueParameters.size == valueParameters.size)
        check(self.type is ClassType)
        check(self.type.clazz.isClassLike()) { "Cannot invoke constructor on self $self" }
    }

    private val methodSpec = MethodSpecialization(method, specialization)

    override fun toString(): String {
        (0 until 1).reversed()
        return "${if (isThis) "this" else "super"}${valueParameters.joinToString(", ", "(", ")")}"
    }

    override fun execute() = eval()
    override fun eval(): BlockReturn {
        val runtime = runtime
        val self = runtime[self]
        check((self.clazz.type as ClassType).clazz.isClassLike()) {
            "Cannot invoke constructor on $self"
        }
        return runtime.executeCall(self, methodSpec, valueParameters).retToVal()
    }
}
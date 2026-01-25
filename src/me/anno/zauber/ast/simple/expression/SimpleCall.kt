package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.simple.FullMap
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.Specialization

class SimpleCall(
    dst: SimpleField,
    // for complex types, e.g. Float|String, we may need multiple methods, or some sort of instance-type->method mapping
    val methodName: String,
    val methods: Map<ClassType, Method>,
    val sample: Method,
    val self: SimpleField,
    val specialization: Specialization,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {

    constructor(
        dst: SimpleField,
        method: Method,
        self: SimpleField,
        specialization: Specialization,
        valueParameters: List<SimpleField>,
        scope: Scope, origin: Int
    ) : this(
        dst, method.name!!, FullMap(method), method, self,
        specialization, valueParameters, scope, origin
    )

    init {
        for (method in methods.values) {
            check(method.valueParameters.size == valueParameters.size)
        }
    }

    override fun toString(): String {
        val method = methods.values.first()
        return "$dst = $self[${method.selfType}].${method.name}${valueParameters.joinToString(", ", "(", ")")}"
    }

    override fun eval(runtime: Runtime): Instance {
        // todo self could be a class instance...
        val self = runtime[self]
        val method = methods[self.type.type as? ClassType] ?: sample
        return runtime.executeCall(self, method, valueParameters)
    }

}
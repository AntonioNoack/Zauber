package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.simple.FullMap
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.specialization.Specialization

class SimpleCall(
    dst: SimpleField,
    // for complex types, e.g. Float|String, we may need multiple methods, or some sort of instance-type->method mapping
    val methodName: String,
    // todo key should also contain specialization, or should it?
    //  method then could be the specialized one...
    val methods: Map<Type, Method>,
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

    override fun eval(runtime: Runtime): BlockReturn {
        val self = runtime[self]
        val method = methods[self.type.type] ?: sample
        val rfm = runtime.executeCall(self, method, valueParameters)
        return if (rfm.type == ReturnType.RETURN) {
            BlockReturn(ReturnType.VALUE, rfm.instance)
        } else rfm
    }

}
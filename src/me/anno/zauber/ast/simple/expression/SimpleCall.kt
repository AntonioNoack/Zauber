package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.simple.FullMap
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.expansion.IsMethodThrowing
import me.anno.zauber.expansion.IsMethodYielding
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.specialization.Specialization

class SimpleCall(
    dst: SimpleField,
    // for complex types, e.g. Float|String, we may need multiple methods, or some sort of instance-type->method mapping
    val methodName: String,
    // todo key should also contain specialization, or should it?
    //  method then could be the specialized one...
    val methods: Map<Type, MethodLike>,
    val sample: MethodLike,
    val self: SimpleField,
    val specialization: Specialization,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleAssignmentExpression(dst, scope, origin) {

    constructor(
        dst: SimpleField,
        method: MethodLike,
        self: SimpleField,
        specialization: Specialization,
        valueParameters: List<SimpleField>,
        scope: Scope, origin: Int
    ) : this(
        dst, (method as? Method)?.name ?: "?", FullMap(method), method, self,
        specialization, valueParameters, scope, origin
    )

    init {
        for (method in methods.values) {
            check(method.valueParameters.size == valueParameters.size)
        }
    }

    // todo these depend on whether all types are instantiable,
    //   and also on available specializations...

    val thrownType: Type by lazy {
        val types = methods.values.map { method -> IsMethodThrowing[method] }
        unionTypes(types)
    }

    val yieldedType: Type by lazy {
        val types = methods.values.map { method -> IsMethodYielding[method] }
        unionTypes(types)
    }

    override fun toString(): String {
        return "$dst = $self[${sample.selfType}].${methodName}${valueParameters.joinToString(", ", "(", ")")}"
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val self = runtime[self]
        val method = methods[self.type.type] ?: sample

        initializeArrayIfNeeded(self, method, runtime)

        val result = runtime.executeCall(self, method, valueParameters)
        return if (result.type == ReturnType.RETURN) {
            BlockReturn(ReturnType.VALUE, result.instance)
        } else result
    }

    fun initializeArrayIfNeeded(self: Instance, method: MethodLike, runtime: Runtime) {
        if (self.type.type.run { this is ClassType && this.clazz.pathStr == "zauber.Array" } &&
            method is Constructor && valueParameters.size == 1 &&
            method.valueParameters[0].type == IntType) {

            val size = runtime.castToInt(runtime[valueParameters[0]])
            self.rawValue = arrayOfNulls<Instance>(size)
        }
    }

}
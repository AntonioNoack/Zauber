package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

class SimpleConstructorCall(
    unusedDst: SimpleField,
    val isThis: Boolean,
    self: SimpleField,
    specialization: Specialization,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Long
) : SimpleCallable(unusedDst, self, specialization, scope, origin) {

    val method get() = sample

    init {
        check(method is Constructor)
        check(method.valueParameters.size == valueParameters.size)
        check(self.type is ClassType)
        check(self.type.clazz.isClassLike()) { "Cannot invoke constructor on self $self" }
    }

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

        initializeArrayIfNeeded(self, method)

        return runtime.executeCall(self, specialization, valueParameters).retToVal()
    }

    fun initializeArrayIfNeeded(self: Instance, method: MethodLike) {
        val selfType = self.clazz.type.resolvedName
        if (selfType is ClassType && selfType.clazz.pathStr == "zauber.Array" &&
            method is Constructor && valueParameters.size == 1 &&
            method.valueParameters[0].type.resolvedName == Types.Int
        ) {
            val sizeParam = valueParameters[0]
            val size = runtime[sizeParam].castToInt()
            self.rawValue = createArray(selfType.typeParameters?.get(0)?.resolvedName, size)
        }
    }

    fun createArray(type: Type?, size: Int): Any {
        return when (type) {
            Types.Boolean -> BooleanArray(size)
            Types.Byte -> ByteArray(size)
            Types.Short -> ShortArray(size)
            Types.Int -> IntArray(size)
            Types.Long -> LongArray(size)
            Types.Float -> FloatArray(size)
            Types.Double -> DoubleArray(size)
            else -> {
                val nullValue = runtime.getNull()
                Array(size) { nullValue }
            }
        }
    }
}
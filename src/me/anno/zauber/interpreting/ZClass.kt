package me.anno.zauber.interpreting

import me.anno.zauber.SpecialFieldNames.OUTER_FIELD_NAME
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.RuntimeCreate.createInt
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.Inheritance
import me.anno.zauber.typeresolution.InsertMode
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization

class ZClass(val type: Type) {

    companion object {
        private val LOGGER = LogManager.getLogger(ZClass::class)

        fun getProperties(type: Type): List<Field> {
            if (type !is ClassType) {
                if (type != NullType) LOGGER.warn("type $type is not a ClassType")
                return emptyList()
            }
            val fields = type.clazz[ScopeInitType.AFTER_DISCOVERY].fields.filter { field ->
                field.needsBackingFieldImpl(type)
            }
            if (type.clazz.isConstructor() && type.clazz.parent!!.scopeType == ScopeType.INNER_CLASS) {
                // move __outer__ to the front
                // todo ideally, it would be sorted inside the scope already...
                return fields.sortedByDescending { it.name == OUTER_FIELD_NAME }
            }
            return fields
        }

        fun Field.needsBackingFieldImpl(type0: Type): Boolean {
            if (isObjectInstance()) return false

            val type = scope.typeWithoutArgs
            // LOGGER.info("[$type0] $this needs backing field? (${!explicitSelfType} || $selfType == $type) && ${needsBackingField()}")
            return (!explicitSelfType || selfType == type) &&
                    needsBackingField()
        }
    }

    val properties = getProperties(type)

    private var objectInstance: Instance? = null

    // todo bug: this is currently used inside methods,
    //  so if a recursive function called itself,
    //  we would have only one instance

    fun getOrCreateObjectInstance(): Instance {
        var objectInstance = objectInstance
        if (objectInstance != null) return objectInstance

        objectInstance = createInstance()
        this.objectInstance = objectInstance

        val scope = (type as? ClassType)?.clazz
        val primaryConstructor = scope?.primaryConstructorScope?.selfAsConstructor
        if (primaryConstructor != null) {
            when (scope.scopeType) {
                null, ScopeType.PACKAGE, ScopeType.OBJECT, ScopeType.COMPANION_OBJECT -> {} // ok
                else -> throw IllegalStateException("Cannot create object instance for $scope")
            }
            check(primaryConstructor.valueParameters.isEmpty()) {
                "Object/package must not have valueParameters, found some in $scope"
            }
            val method1 = MethodSpecialization(primaryConstructor, noSpecialization)
            runtime.executeCall(objectInstance, method1, emptyList())
        }
        return objectInstance
    }

    fun createInstance(): Instance {
        if (type == NullType || type == Types.Nothing) {
            throw IllegalStateException("Cannot create instance of $type")
        }
        if (type !is ClassType) {
            throw IllegalStateException("Type to create must be concrete and fully specified ($type)")
        }
        return Instance(this, arrayOfNulls(properties.size), runtime.nextInstanceId())
    }

    fun createArray(items: Array<Instance>): Instance {
        val rt = runtime
        val clazz = rt.getClass(Types.Array.withTypeParameter(type))
        val result = clazz.createInstance()
        result.set("size", rt.createInt(items.size))
        result.rawValue = items
        return result
    }

    fun isSubTypeOf(expectedType: ZClass): Boolean {
        return Inheritance.isSubTypeOf(
            expectedType.type,
            type, emptyList(), ParameterList.emptyParameterList(),
            InsertMode.READ_ONLY
        )
    }

    val isValueClass: Boolean =
        type is ClassType && type.clazz.flags.hasFlag(Flags.VALUE)

    override fun toString(): String {
        return "ZClass($type,${properties.map { it.name }})"
    }
}
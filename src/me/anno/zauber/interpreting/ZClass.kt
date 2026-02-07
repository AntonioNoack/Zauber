package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.typeresolution.Inheritance
import me.anno.zauber.typeresolution.InsertMode
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class ZClass(val type: Type) {

    companion object {
        fun getProperties(type: Type): List<Field> {
            if (type !is ClassType) return emptyList()
            // val scopeType = type.clazz.scopeType
            // val isValidInstance = scopeType == null || scopeType == ScopeType.PACKAGE || scopeType.isClassType()
            // if (!isValidInstance) return emptyList()
            return type.clazz.fields.filter {
                it.needsBackingFieldImpl()
            }
        }

        fun Field.needsBackingFieldImpl(): Boolean {
            val type = codeScope.typeWithoutArgs
            return (!explicitSelfType || selfType == type) &&
                    needsBackingField()
        }
    }

    val properties = getProperties(type)

    private var objectInstance: Instance? = null

    // todo bug: this is currently used inside methods,
    //  so if a recursive function called itself,
    //  we would have only one instance
    fun getOrCreateObjectInstance(runtime: Runtime): Instance {
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
            runtime.executeCall(objectInstance, primaryConstructor, emptyList())
        }
        return objectInstance
    }

    fun createInstance(): Instance {
        return Instance(this, arrayOfNulls(properties.size))
    }

    fun isSubTypeOf(expectedType: ZClass): Boolean {
        return Inheritance.isSubTypeOf(
            expectedType.type,
            type, emptyList(), ParameterList.emptyParameterList(),
            InsertMode.READ_ONLY
        )
    }

    override fun toString(): String {
        return "ZClass($type,${properties.map { it.name }})"
    }
}
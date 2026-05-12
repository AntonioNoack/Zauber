package me.anno.generation.rust

import me.anno.generation.java.JavaSourceGenerator.Companion.isStoredField
import me.anno.generation.rust.RustSourceGenerator.Companion.nativeRustTypes
import me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME
import me.anno.zauber.expansion.GraphColoring
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.arithmetic.AndType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType
import me.anno.zauber.types.specialization.Specialization

object RustOwnership : GraphColoring<Type, RustOwnershipType>() {

    override fun getDependencies(key: Type): Collection<Type> {
        return when (key) {
            in nativeRustTypes -> emptyList()
            is ClassType -> {
                val spec = Specialization(key)
                key.clazz.fields
                    .filter { field -> isStoredField(field) }
                    .map { field ->
                        check(field.name != OBJECT_FIELD_NAME)
                        field.valueType!!.specialize(spec)
                    }
            }
            is UnionType, is AndType -> key.types
            is GenericType -> getDependencies(key.superBounds)
            NullType -> emptyList()
            else -> throw NotImplementedError("Find fields in $key (${key.javaClass.simpleName})")
        }
    }

    override fun getSelfColor(key: Type): RustOwnershipType {
        return when (key) {
            is ClassType -> when {
                key.clazz.isValueType() -> RustOwnershipType.IMMUTABLE
                key in RustSourceGenerator.nativeRustNumbers -> RustOwnershipType.IMMUTABLE
                else -> RustOwnershipType.FLAT_MUTABLE // pointer is necessary for ===
            }
            // todo this could become a special enum :3, but we would need to be extra careful with casting
            is UnionType -> if (NullType in key.types) RustOwnershipType.DEEP_MUTABLE_OR_NULL else RustOwnershipType.DEEP_MUTABLE
            is GenericType -> getSelfColor(key.superBounds)
            NullType -> RustOwnershipType.IMMUTABLE
            else -> throw NotImplementedError("Find fields in $key (${key.javaClass.simpleName})")
        }
    }

    override fun mergeColors(
        key: Type,
        self: RustOwnershipType,
        colors: List<RustOwnershipType>,
        isRecursive: Boolean
    ): RustOwnershipType {

        if (key in nativeRustTypes) {
            return RustOwnershipType.IMMUTABLE
        }

        if (colors.isEmpty()) {
            check(!isRecursive)
            // cannot be recursive -> fine
            return self
        }

        // a number or value class
        if (!isRecursive && self.isImmutable &&
            colors.all { it.isImmutable }
        ) return self

        // check whether we need GC
        if (isRecursive || self.isDeepMutable || colors.any { it.isDeepMutable }) {
            return if (self.isNullable) RustOwnershipType.DEEP_MUTABLE_OR_NULL
            else RustOwnershipType.DEEP_MUTABLE
        }

        // else we just need a wrapper
        return if (self.isNullable) RustOwnershipType.FLAT_MUTABLE_OR_NULL
        else RustOwnershipType.FLAT_MUTABLE
    }
}
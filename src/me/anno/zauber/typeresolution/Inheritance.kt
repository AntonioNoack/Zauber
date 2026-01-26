package me.anno.zauber.typeresolution

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.SuperCall
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.AnyType
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.impl.*

/**
 * Check if one type inherits from another, incl. generic checks.
 * */
object Inheritance {

    private val LOGGER = LogManager.getLogger(Inheritance::class)

    fun isSubTypeOf(
        selfTypeIfNeeded: Type?,
        expected: Parameter,
        actual: ValueParameter,
        expectedTypeParameters: List<Parameter>,
        actualTypeParameters: ParameterList,
        insertMode: InsertMode
    ): Boolean {
        val expectedType = actualTypeParameters.resolveGenerics(selfTypeIfNeeded, expected.type)
        if (insertMode == InsertMode.READ_ONLY &&
            actualTypeParameters.types.any { it == null }
        ) {
            throw IllegalArgumentException("ReadOnly but unknown types?")
        }

        if (expected.type != expectedType) {
            LOGGER.info("Resolved ${expected.type} to $expectedType for isSubTypeOf")
        }

        val actualType = actual.getType(expectedType)
        LOGGER.info("ActualType[$actual,$expectedType] -> $actualType")
        return isSubTypeOf(
            expectedType, actualType,
            expectedTypeParameters, actualTypeParameters,
            insertMode
        )
    }

    fun isSubTypeOf(
        expectedType: Type,
        actualType: Type,
        expectedTypeParams: List<Parameter>,
        actualTypeParameters: List<Type?>,
        insertMode: InsertMode,
    ): Boolean {
        LOGGER.info("Checking $actualType instanceOf $expectedType")
        LOGGER.info("  with generics $expectedTypeParams vs $actualTypeParameters")
        LOGGER.info("  and insertMode $insertMode")
        val result = isSubTypeOfImpl(
            expectedType,
            actualType,
            expectedTypeParams,
            actualTypeParameters,
            insertMode,
        )
        LOGGER.info("  got $result for $actualType instanceOf $expectedType")
        return result
    }

    private fun tryInsertGenericType(
        expectedType: GenericType,
        actualType: Type,
        expectedTypeParams: List<Parameter>,
        actualTypeParameters: List<Type?>,
        insertMode: InsertMode
    ): Boolean {
        check(insertMode != InsertMode.READ_ONLY)

        val typeParamIdx = expectedTypeParams.indexOfFirst {
            it.name == expectedType.name &&
                    it.scope == expectedType.scope
        }

        if (typeParamIdx == -1) {
            if (insertMode != InsertMode.WEAK) {
                LOGGER.warn("Missing generic parameter ${expectedType.name}, ignoring it")
            }// else can be safely ignored ;)
            return true
        }

        actualTypeParameters as ParameterList

        val expectedTypeParam = expectedTypeParams[typeParamIdx]
        if (!isSubTypeOf(
                // check bounds of expectedTypeParam
                expectedTypeParam.type,
                actualType,
                expectedTypeParams,
                actualTypeParameters,
                InsertMode.READ_ONLY,
            )
        ) return false

        val success = actualTypeParameters.union(typeParamIdx, actualType, insertMode)
        LOGGER.info("Found Type[$success for $actualType @$insertMode vs ${actualTypeParameters.insertModes[typeParamIdx]}]: " +
                "[$typeParamIdx,${expectedType.scope.pathStr}.${expectedType.name}] = ${actualTypeParameters[typeParamIdx]}")
        return success
    }

    private fun isSubTypeOfImpl(
        expectedType: Type,
        actualType: Type,
        expectedTypeParams: List<Parameter>,
        actualTypeParameters: List<Type?>,
        insertMode: InsertMode,
    ): Boolean {

        if (expectedType == actualType) return true
        if (expectedType == NullableAnyType) return true
        if (expectedType == UnknownType) return true

        if (actualType == UnknownType) {
            // todo use the bounds of the generics instead, not 'Any?'
            return isSubTypeOf(
                expectedType, NullableAnyType,
                expectedTypeParams, actualTypeParameters, insertMode
            )
        }

        if (expectedType is NotType) {
            return !isSubTypeOf(
                expectedType.not(), actualType,
                expectedTypeParams, actualTypeParameters, insertMode
            )
        }

        if (actualType is NotType) {
            return !isSubTypeOf(
                expectedType, actualType.not(),
                expectedTypeParams, actualTypeParameters, insertMode
            )
        }

        if (actualType is UnionType && expectedType !is GenericType) {
            // everything must fit
            // first try without inserting types
            val t0 = actualType.types.all { allActual ->
                isSubTypeOf(
                    expectedType, allActual,
                    expectedTypeParams,
                    actualTypeParameters,
                    InsertMode.READ_ONLY,
                )
            }
            if (t0 || insertMode == InsertMode.READ_ONLY) return t0
            // then, try with inserting new types
            return actualType.types.all { allActual ->
                isSubTypeOf(
                    expectedType, allActual,
                    expectedTypeParams,
                    actualTypeParameters,
                    insertMode,
                )
            }
        }

        if (expectedType is UnionType) {
            // first try without inserting types
            val t0 = expectedType.types.any { anyExpected ->
                isSubTypeOf(
                    anyExpected, actualType,
                    expectedTypeParams,
                    actualTypeParameters,
                    InsertMode.READ_ONLY
                )
            }
            if (t0 || insertMode == InsertMode.READ_ONLY) return t0
            // then, try with inserting new types
            return expectedType.types.any { anyExpected ->
                isSubTypeOf(
                    anyExpected, actualType,
                    expectedTypeParams,
                    actualTypeParameters,
                    insertMode
                )
            }
        }

        if (expectedType is AndType) {
            // first try without inserting types
            val t0 = expectedType.types.all { anyExpected ->
                isSubTypeOf(
                    anyExpected, actualType,
                    expectedTypeParams,
                    actualTypeParameters,
                    InsertMode.READ_ONLY
                )
            }
            if (t0 || insertMode == InsertMode.READ_ONLY) return t0
            // then, try with inserting new types
            return expectedType.types.all { anyExpected ->
                isSubTypeOf(
                    anyExpected, actualType,
                    expectedTypeParams,
                    actualTypeParameters,
                    insertMode
                )
            }
        }

        if (actualType is AndType) {
            // everything must fit
            // first try without inserting types
            val t0 = actualType.types.any { allActual ->
                isSubTypeOf(
                    expectedType, allActual,
                    expectedTypeParams,
                    actualTypeParameters,
                    InsertMode.READ_ONLY,
                )
            }
            if (t0 || insertMode == InsertMode.READ_ONLY) return t0
            // then, try with inserting new types
            return actualType.types.any { allActual ->
                isSubTypeOf(
                    expectedType, allActual,
                    expectedTypeParams,
                    actualTypeParameters,
                    insertMode,
                )
            }
        }

        if (insertMode != InsertMode.READ_ONLY) {
            if (actualType is GenericType) {
                return tryInsertGenericType(
                    // does this work with just swapping them???
                    actualType, expectedType,
                    expectedTypeParams, actualTypeParameters,
                    insertMode,
                )
            }

            if (expectedType is GenericType) {
                return tryInsertGenericType(
                    expectedType, actualType,
                    expectedTypeParams, actualTypeParameters,
                    insertMode,
                )
            }
        }

        if ((expectedType == NullType) != (actualType == NullType)) {
            return false
        }

        if (false) LOGGER.info(
            "checkingEq: $expectedType vs $actualType " +
                    "-> ${expectedType == actualType}"
        )

        if (expectedType == actualType) return true
        if (actualType is ClassType && expectedType is ClassType) {
            if (expectedType.clazz == actualType.clazz) {
                val actualGenerics = actualType.typeParameters
                val expectedGenerics = expectedType.typeParameters
                if (expectedGenerics == null) {
                    LOGGER.info("Nothing is expected for generics, matching")
                    return true
                }

                if (actualGenerics == null /*&&
                    expectedGenerics.all {
                        it is GenericType &&
                                expectedTypeParams.none { p -> p.scope == it.scope && p.name == it.name }
                    }*/
                ) {
                    LOGGER.info("Actual generics unknown -> continue with true (?)")
                    return true
                }

                val actualSize = actualGenerics.size
                val expectedSize = expectedGenerics.size
                LOGGER.info("Class vs Class (${actualType.clazz.name}), $actualSize vs $expectedSize, $insertMode")

                if (actualSize != expectedSize) {
                    LOGGER.info("Mismatch in generic count :(")
                    return false
                }

                // todo in/out now matters for the direction of the isSubTypeOf...
                for (i in actualGenerics.indices) {
                    // these may be null, if so, just accept them
                    val expectedType = expectedGenerics.getOrNull(i) ?: continue
                    val actualType = actualGenerics.getOrNull(i) ?: continue

                    if (!isSubTypeOf(
                            expectedType, actualType,
                            expectedTypeParams, actualTypeParameters,
                            insertMode,
                        )
                    ) return false
                }
                return true
            }

            // LOGGER.info("classType of $expectedType: ${expectedType.clazz.scopeType}")

            // check super class
            // todo if super type has generics, we need to inject them into the super type
            return getSuperCalls(actualType.clazz).any { superCall ->
                val superType = superCall.type
                LOGGER.info("super($actualType): $superType")
                isSubTypeOf(
                    expectedType,
                    superType,
                    expectedTypeParams,
                    actualTypeParameters,
                    insertMode,
                )
            }
        }

        if ((actualType is LambdaType) != (expectedType is LambdaType)) {
            return false
        }

        if (actualType is LambdaType && expectedType is LambdaType) {
            if (expectedType.parameters.size != actualType.parameters.size) return false

            return isSubTypeOf(
                // return type is one direction, actual type is the other...
                //  params are normal, return type is the other way around...
                //  -> this needs to be flipped
                actualType.returnType, expectedType.returnType,
                expectedTypeParams, actualTypeParameters,
                insertMode,
            ) && expectedType.parameters.indices.all { paramIndex ->
                isSubTypeOf(
                    expectedType.parameters[paramIndex].type,
                    actualType.parameters[paramIndex].type,
                    expectedTypeParams, actualTypeParameters,
                    insertMode,
                )
            }
        }

        if (insertMode == InsertMode.READ_ONLY) {
            if (expectedType is GenericType || actualType is GenericType) {
                val expectedType = if (expectedType is GenericType) expectedType.superBounds else expectedType
                val actualType = if (actualType is GenericType) actualType.superBounds else expectedType
                LOGGER.info("Using superBounds for insertMode=READ_ONLY")
                return isSubTypeOf(
                    expectedType, actualType,
                    expectedTypeParams, actualTypeParameters,
                    insertMode,
                )
            }
        }

        TODO("Is $actualType (${actualType.javaClass.simpleName}) a $expectedType (${expectedType.javaClass.simpleName})?, $expectedTypeParams, $actualTypeParameters [$insertMode]")
    }

    fun getSuperCalls(scope: Scope): List<SuperCall> {
        if (scope == AnyType.clazz) return emptyList()
        if (scope.superCalls.isEmpty()) return listOf(superCallAny)
        return scope.superCalls
    }

    private val superCallAny = SuperCall(AnyType, emptyList(), null)

}
package me.anno.generation.jvm

import me.anno.generation.java.JavaSourceGenerator.Companion.resolveType
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.SimpleMerge
import me.anno.zauber.ast.simple.fields.LocalField
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import kotlin.math.max

/**
 * Maps SimpleFields to JVM local variable slots.
 *
 * We coalesce phi/merge groups by mapping merged SimpleFields to the same slot.
 */
class JvmLocals(
    private val gen: JVMBytecodeGenerator,
    private val code: JvmCodeBuilder,
    graph: SimpleGraph
) {

    val maxLocals: Int

    private val localSlots = HashMap<LocalField, Int>()
    private val fieldSlots = HashMap<SimpleField, Int>()

    private val parent = HashMap<SimpleField, SimpleField>()

    init {
        // Union-find across merge instructions: (dst, ifField, elseField) share one local.
        for (block in graph.blocks) {
            for (instr in block.instructions) {
                val m = instr as? SimpleMerge ?: continue
                union(m.dst, m.ifField)
                union(m.dst, m.elseField)
            }
        }

        var slot = 0

        // instance methods always have "this" at slot 0
        val method = graph.method
        if (method.ownerScope.isClassLike()) {
            graph.thisField?.let { lf ->
                localSlots[lf] = slot
                slot++
            }
        }

        if (method.hasExplicitSelfType) {
            graph.selfField?.let { lf ->
                localSlots[lf] = slot
                slot += slotSize(lf.type)
            }
        }

        for (p in graph.parameterFields) {
            localSlots[p] = slot
            slot += slotSize(p.type)
        }

        // other LocalFields (mutable locals)
        for (lf in graph.localFields) {
            if (lf in localSlots) continue
            localSlots[lf] = slot
            slot += slotSize(lf.type)
        }

        // SimpleFields (SSA temps) -> locals
        for (sf0 in graph.simpleFields) {
            val sf = rep(sf0)
            if (sf in fieldSlots) continue
            fieldSlots[sf] = slot
            slot += slotSize(sf.type)
        }

        maxLocals = max(1, slot)
    }

    private fun find(x: SimpleField): SimpleField {
        val p = parent[x] ?: return x
        val r = find(p)
        parent[x] = r
        return r
    }

    private fun union(a: SimpleField, b: SimpleField) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[rb] = ra
    }

    private fun rep(f: SimpleField): SimpleField = find(f)

    private fun slotSize(type0: Type): Int {
        val t = resolveType(type0)
        return when (t) {
            Types.Long, Types.ULong, Types.Double -> 2
            else -> 1
        }
    }

    fun loadLocal(local: LocalField) {
        val slot = localSlots[local] ?: error("Missing slot for $local")
        code.loadByType(slot, resolveType(local.type))
    }

    fun storeLocal(local: LocalField) {
        val slot = localSlots[local] ?: error("Missing slot for $local")
        val t = resolveType(local.type)
        if (isI32Like(t)) code.istore(slot) else code.astore(slot)
    }

    fun loadField(field0: SimpleField) {
        val t = resolveType(field0.type)
        // constants are modeled via constantRef on the field itself; JavaSourceGenerator would inline them.
        val constant = field0.constantRef
        if (constant is NumberExpression) {
            pushConst(field0, constant)
            return
        }
        // object-likes: load __object__
        if (t is ClassType && t.clazz.isObjectLike()) {
            val internal = gen.internalNameOf(t)
            code.getstatic(internal, me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME, "L$internal;")
            return
        }

        val fromLocal = field0.fromLocalField
        if (fromLocal != null) {
            loadLocal(fromLocal)
            return
        }

        val field = rep(field0)
        val slot = fieldSlots[field] ?: error("Missing slot for field $field")
        if (isI32Like(t)) code.iload(slot) else code.aload(slot)
    }

    fun storeField(field0: SimpleField) {
        val field = rep(field0)
        val slot = fieldSlots[field] ?: error("Missing slot for field $field")
        val t = resolveType(field.type)
        if (isI32Like(t)) code.istore(slot) else code.astore(slot)
    }

    fun pushConst(dst: SimpleField, num: NumberExpression) {
        when (val t = resolveType(dst.type)) {
            Types.Boolean, Types.Byte, Types.Short, Types.Char, Types.Int, Types.UInt -> code.iconst(num.asInt.toInt())
            Types.Long, Types.ULong -> code.lconst(num.asInt)
            Types.Float, Types.Half -> code.fconst(num.asFloat.toFloat())
            Types.Double -> code.dconst(num.asFloat)
            else -> error("Unexpected constant number $t")
        }
    }

    private fun isI32Like(t: Type): Boolean {
        val rt = resolveType(t)
        return rt == Types.Boolean ||
                rt == Types.Byte || rt == Types.UByte ||
                rt == Types.Short || rt == Types.UShort ||
                rt == Types.Char ||
                rt == Types.Int || rt == Types.UInt
    }
}

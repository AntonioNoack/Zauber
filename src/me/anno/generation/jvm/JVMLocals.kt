package me.anno.generation.jvm

import me.anno.generation.java.JavaSourceGenerator.Companion.resolveType
import me.anno.generation.jvm.JVMBytecodeGenerator.Companion.toJVMValueType
import me.anno.zauber.SpecialFieldNames
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.SimpleMerge
import me.anno.zauber.ast.simple.fields.LocalField
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import kotlin.math.max

/**
 * Maps SimpleFields to JVM local variable slots.
 *
 * We coalesce phi/merge groups by mapping merged SimpleFields to the same slot.
 */
class JVMLocals(
    private val gen: JVMBytecodeGenerator,
    private val code: JVMCodeBuilder,
    private val graph: SimpleGraph
) {

    private val localSlots = HashMap<LocalField, Int>()
    private val fieldSlots = HashMap<SimpleField, Int>()
    val orderedLocals = ArrayList<LocalField>()

    private val parent = HashMap<SimpleField, SimpleField>()

    val maxLocals: Int = collectFields(graph)

    fun snapshotForFrame(cp: ConstantPool): List<VerificationTypeInfo> {

        if (maxLocals <= 0) return emptyList()

        val localsBySlot = arrayOfNulls<VerificationTypeInfo>(maxLocals)

        // Real JVM locals only.
        // SSA temporaries in fieldSlots are NOT verifier locals.
        for ((local, slot) in localSlots) {

            val vt = JVMStackMapBuilder.typeOf(local.type, gen, cp)

            localsBySlot[slot] = vt

            // category-2 locals occupy two slots
            when (vt) {
                VerificationTypeInfo.LongVariable,
                VerificationTypeInfo.DoubleVariable -> {
                    if (slot + 1 < localsBySlot.size) {
                        localsBySlot[slot + 1] = VerificationTypeInfo.TopVariable
                    }
                }
                else -> {}
            }
        }

        // trailing TOP entries must be removed
        var end = localsBySlot.size
        while (end > 0 && localsBySlot[end - 1] == null) {
            end--
        }

        return buildList(end) {
            for (i in 0 until end) {
                add(localsBySlot[i] ?: VerificationTypeInfo.TopVariable)
            }
        }
    }

    private fun collectFields(graph: SimpleGraph): Int {
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
                assignSlot(lf, slot)
                slot++
            }
        }

        if (method.hasExplicitSelfType) {
            graph.selfField?.let { lf ->
                assignSlot(lf, slot)
                slot += slotSize(lf.type)
            }
        }

        for (p in graph.parameterFields) {
            localSlots[p] = slot
            orderedLocals.add(p)
            slot += slotSize(p.type)
        }

        // other LocalFields (mutable locals)
        for (lf in graph.localFields) {
            if (lf in localSlots) continue
            assignSlot(lf, slot)
            slot += slotSize(lf.type)
        }

        // SimpleFields (SSA temps) -> locals
        for (sf0 in graph.simpleFields) {
            val sf = rep(sf0)
            if (sf in fieldSlots) continue
            fieldSlots[sf] = slot
            slot += slotSize(sf.type)
        }

        return max(1, slot)
    }

    private fun assignSlot(lf: LocalField, slot: Int) {
        localSlots[lf] = slot
        orderedLocals.add(lf)
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

    private fun rep(f: SimpleField): SimpleField = find(f.dst)

    private fun slotSize(type0: Type): Int {
        return when (toJVMValueType(type0)) {
            JVMValueType.LONG, JVMValueType.DOUBLE -> 2
            else -> 1
        }
    }

    fun loadLocal(local: LocalField) {
        val slot = localSlots[local] ?: error("Missing slot for $local")
        code.load(toJVMValueType(local.type), slot)
    }

    fun storeLocal(local: LocalField) {
        val slot = localSlots[local] ?: error("Missing slot for $local")
        code.store(toJVMValueType(local.type), slot)
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
            // In objects, __object__ may not be initialized yet; use "this" for that object.
            val isInsideObject = t.clazz == graph.method.ownerScope
            if (isInsideObject) {
                code.aload0()
            } else {
                val internal = gen.getJVMName(t)
                code.getstatic(internal, SpecialFieldNames.OBJECT_FIELD_NAME, "L$internal;")
            }
            return
        }

        val fromLocal = field0.fromLocalField
        if (fromLocal != null) {
            loadLocal(fromLocal)
            return
        }

        val field = rep(field0)
        val slot = fieldSlots[field] ?: error("Missing slot for field $field")
        code.load(toJVMValueType(field.type), slot)
    }

    fun storeField(field0: SimpleField) {
        val field = rep(field0)
        val slot = fieldSlots[field] ?: error("Missing slot for field $field")
        code.store(toJVMValueType(field.type), slot)
    }

    fun pushConst(dst: SimpleField, num: NumberExpression) {
        when (toJVMValueType(dst.type)) {
            JVMValueType.INT -> code.iconst(num.asInt.toInt())
            JVMValueType.LONG -> code.lconst(num.asInt)
            JVMValueType.FLOAT -> code.fconst(num.asFloat.toFloat())
            JVMValueType.DOUBLE -> code.dconst(num.asFloat)
            JVMValueType.REFERENCE -> error("Unexpected numeric constant as reference: ${dst.type}")
        }
    }

}

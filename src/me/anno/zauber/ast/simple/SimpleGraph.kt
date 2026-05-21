package me.anno.zauber.ast.simple

import me.anno.utils.StringStyles.bold
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.simple.controlflow.FlowResult
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.ast.simple.expression.SimpleConstructorCall
import me.anno.zauber.ast.simple.fields.LocalField
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

class SimpleGraph(val method0: Specialization) {

    val method = method0.method

    private var numFields = 0
    val blocks = ArrayList<SimpleBlock>()
    val startBlock = SimpleBlock(this)
    val simpleFields = ArrayList<SimpleField>()
    val localFields = ArrayList<LocalField>()

    // these cannot be SimpleField, because we join SimpleFields,
    //  and these cannot be properly joined (except for LLVM)
    var thisField: LocalField? = null
    var selfField: LocalField? = null
    var parameterFields: List<LocalField> = emptyList()

    // typically used lots in graphs to represent no value, e.g. from while-loops
    var unitField: SimpleField? = null

    val capturedFields = HashMap<Capture, SimpleField>()

    val continueLabels = HashMap<Scope, SimpleBlock>()
    val breakLabels = HashMap<Scope, SimpleBlock>()

    lateinit var endFlow: FlowResult

    init {
        startBlock.isEntryPoint = true
        blocks.add(startBlock)
    }

    fun addBlock(): SimpleBlock {
        val node = SimpleBlock(this)
        blocks.add(node)
        return node
    }

    fun field(type: Type, constantRef: Expression? = null): SimpleField {
        val field = SimpleField(type, numFields++, constantRef)
        simpleFields.add(field)
        return field
    }

    fun getOrPutLocalField(field: Field, context: ResolutionContext): LocalField {
        var localField = localFields
            .firstOrNull { it.field == field }
        if (localField == null) {
            val type = field.resolveValueType(context)
            localField = createLocalField(field, field.name, type)
        }
        return localField
    }

    fun initializeSpecialFields(context: ResolutionContext) {
        val method = method
        if (method.ownerScope.isClassLike()) {
            val selfType = method.ownerScope.typeWithArgs.specialize(context)
            thisField = createLocalField(null, "this", selfType)
        }
        if (method.hasExplicitSelfType) {
            val selfType = method.selfType!!.specialize(context)
            selfField = createLocalField(null, "self", selfType)
        }
        parameterFields = method.valueParameters.map { parameter ->
            val type = parameter.type.specialize(context)
            createLocalField(parameter.field!!, parameter.name, type)
        }
    }

    private fun createLocalField(field: Field?, name: String, type: Type): LocalField {
        val localField = LocalField(field, name, type, localFields.size)
        localFields.add(localField)
        return localField
    }

    fun onCapturedField(field: Field) {
        field.isCaptured = true
        method.capturedFields += field // todo may be specialization dependant...
    }

    fun readCapturedField(owner: MethodLike, field: Field, valueType: Type): SimpleField {
        onCapturedField(field)
        return capturedFields.getOrPut(Capture(owner, field)) { field(valueType) }
    }

    override fun toString(): String {
        return "${bold("Graph")}[${blocks.size} nodes, $numFields fields]\n" +
                "${bold("this:")} $thisField\n" +
                "${bold("self:")} $selfField\n" +
                "${bold("unit:")} $unitField\n" +
                "${bold("params:")} $parameterFields\n" +
                blocks.joinToString("\n") { it.toString() }
    }

    fun removeSuperCalls() {
        for (block in blocks) {
            block.instructions.removeIf {
                it is SimpleConstructorCall
            }
        }
    }

    fun removeConstantFields() {
        removeFieldIf { it.constantRef != null }
        for (block in blocks) {
            block.instructions.removeIf {
                it is SimpleAssignment &&
                        it.dst.constantRef != null
            }
        }
    }

    fun removeObjectFields() {
        removeFieldIf {
            val type = it.type
            type is ClassType && type.clazz.isObjectLike()
        }
    }

    fun removeWriteOnlyFields() {
        removeFieldIf { it.numReads == 0 }
    }

    fun removeFieldsByLocalFields() {
        removeFieldIf { it.fromLocalField != null && it.mergeInfo == null }
    }

    fun removeMergedFields() {
        removeFieldIf { it.mergeInfo != null }
    }


    fun replaceYieldsByInnerClass() {
        // todo if inner classes/methods reference a mutable field,
        //  create an inner class, too
        TODO()
    }

    fun inlineValueClasses() {
        TODO()
    }

    fun removeFieldIf(condition: (SimpleField) -> Boolean) {
        simpleFields.removeIf { field ->
            if (condition(field)) {
                field.id = -100_000 - field.id
                check(field.id < 0)
                true
            } else false
        }
    }

    fun renumberFields() {
        for (i in simpleFields.indices) {
            simpleFields[i].id = i
        }
    }

    fun giveLocalFieldsUniqueNames() {
        val foundNames = HashMap<String, LocalField?>()

        // protect these special names:
        foundNames["this"] = null
        if (method.hasExplicitSelfType) foundNames["self"] = null
        for (param in method.valueParameters) foundNames[param.name] = null

        for (field in localFields) {
            foundNames.getOrPut(field.name) { field }
        }

        for (field in localFields) {
            val prevField = foundNames[field.name]
            if (prevField != field) findNewName(foundNames, field)
        }
    }

    private fun findNewName(allNames: HashMap<String, LocalField?>, field: LocalField) {
        val oldName = field.name
        var id = 0
        while (true) {
            val newName = "${oldName}_${id++}"
            if (newName !in allNames) {
                allNames[newName] = field
                field.name = newName
                return
            }
        }
    }

}
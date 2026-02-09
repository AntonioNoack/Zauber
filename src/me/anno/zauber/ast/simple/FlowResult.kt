package me.anno.zauber.ast.simple

import me.anno.zauber.ast.simple.Flow.Companion.join

data class FlowResult(val value: Flow?, val returned: Flow?, val thrown: Flow?) {

    fun withValue(field: SimpleField, block: SimpleNode): FlowResult {
        return FlowResult(Flow(field, block), returned, thrown)
    }

    fun withThrown(field: SimpleField, block: SimpleNode): FlowResult {
        return FlowResult(value, returned, Flow(field, block))
    }

    /**
     * joins returned and thrown flows;
     * value is unknown/null
     * */
    fun joinError(other: FlowResult?): FlowResult {
        if (other == null) return this
        if (other.thrown == null && other.returned == null) return this
        if (thrown == null && returned == null) return other
        return FlowResult(null, returned.join(other.returned), thrown.join(other.thrown))
    }

    /**
     * joins returned and thrown flows;
     * value is unknown/null
     * */
    fun joinError(other: Flow): FlowResult {
        return FlowResult(null, returned, thrown.join(other))
    }

    fun joinReturnNoValue(newReturnValue: SimpleField, newReturnBlock: SimpleNode): FlowResult {
        val joinedValue = returned.join(newReturnValue, newReturnBlock)
        return FlowResult(null, joinedValue, thrown)
    }

    fun joinThrownNoValue(newThrownValue: SimpleField, newThrownBlock: SimpleNode): FlowResult {
        val joinedValue = thrown.join(newThrownValue, newThrownBlock)
        return FlowResult(null, returned, joinedValue)
    }

    fun joinWith(other: FlowResult): FlowResult {
        return FlowResult(value.join(other.value), returned.join(other.returned), thrown.join(other.thrown))
    }

    fun withValue(newValue: SimpleField): FlowResult {
        if (value == null) return this
        return withValue(newValue, value.block)
    }

    fun thrownToValue() = FlowResult(thrown, null, null)
    fun returnToValue() = FlowResult(returned, null, null)
    fun valueToReturn(returned: Flow): FlowResult {
        check(this.returned == null) { "Finally-block for return must not return itself" }
        if (value == null) return this
        return FlowResult(null, Flow(returned.value, value.block), thrown)
    }

    fun valueToThrown(returned: Flow): FlowResult {
        check(this.returned == null) { "Finally-block for catch must not return" }
        if (value == null) return this
        return FlowResult(null, returned, thrown.join(returned.value, value.block))
    }
}
package me.anno.generation.rust

import me.anno.zauber.ast.rich.Flags.hasFlag

enum class RustOwnershipType(
    val flags: Int,
    val typePrefix: String, val typeSuffix: String,
    val allocPrefix: String = typePrefix, val allocSuffix: String = typeSuffix,
    val callPrefix: String = ""
) {
    IMMUTABLE(0, "", ""), // e.g. for Int or Vector
    IMMUTABLE_OR_NULL(1, "Option<", ">"), // e.g. for Int?

    /**
     * e.g. for Arrays or ArrayList<Int>
     * */
    FLAT_MUTABLE(2, "RefCell<", ">", "RefCell::new(", ")", ".borrow_mut()"),

    FLAT_MUTABLE_OR_NULL(3, "RefCell<", ">"),

    DEEP_MUTABLE(4, "GcCell<Gc<", ">>"), // e.g. for Panel
    DEEP_MUTABLE_OR_NULL(5, "GcCell<Option<Gc<", ">>>"), // e.g. for Panel?

    ;

    val isImmutable: Boolean get() = flags < 2
    val isNullable: Boolean get() = flags.hasFlag(1)
    val isFlatMutable: Boolean get() = flags.hasFlag(2)
    val isDeepMutable: Boolean get() = flags.hasFlag(4)
}

package me.anno.zauber.ast.simple

enum class Ownership {
    /**
     * mutable,
     * indestructible [<pointer> -> <data>],
     * e.g. objects
     *
     * can be converted to OWNED by creating a light-weight struct { nullptr, data-pointer }
     * no GC necessary
     * pointer-comparison compares pointer
     * */
    COMPTIME,

    /**
     * mutable,
     * owned by runtime, { class-index, pointer data }
     * before data, there must be the GC-header (size, counter)
     * e.g. List<String>, Thread
     *
     * can be converted to OWNED by creating a light-weight struct { owner, data-pointer }
     * fully GCed
     * pointer-comparison compares data-pointer
     * */
    SHARED,

    /**
     * mutable,
     * owned by sth else: { shared owner, class-index, pointer data }
     * e.g. array entries, object entries
     *
     * used for call arguments;
     * GC only happens on owner
     * pointer-comparison compares data-pointer
     * */
    OWNED,

    /**
     * immutable struct: [<data>]
     * e.g. int, Vector3f
     *
     * used for call arguments
     * no GC necessary
     * pointer-comparison always returns... true? false?
     * todo if any member is not used, it can/could be dropped (e.g. class)
     * */
    VALUE,
}
package me.anno.zauber.typeresolution

enum class InsertMode(val symbol: String) {
    /**
     * entries that are added are only weak
     * */
    WEAK("w"),
    /**
     * list should be union-ed
     * */
    STRONG("s"),
    /**
     * list is read-only
     * */
    READ_ONLY("ro"),
}
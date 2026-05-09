package me.anno.generation

/**
 * some types exist in two forms, native and boxed;
 * in native form, many functions cannot be called
 * */
data class BoxedType(val boxed: String, val native: String)
package me.anno.generation.glsl

import me.anno.generation.c.CSourceGenerator

// todo generate compilable GLSL, including uniform buffers and bindings to get the necessary context data
// todo big difference to C: allocations are only possible on the stack, pointers are limited to out-variables
//  -> we have a global buffer/shared memory for dynamic allocations
//  same as with C, we don't have inheritance built-in
/**
 * this is like C, but we don't have proper pointers,
 * so reading from data must always happen via buffers
 *
 * todo we have some Vector- and Matrix-types predefined. It would be good to make use of them
 * */
class GLSLSourceGenerator : CSourceGenerator() {

}
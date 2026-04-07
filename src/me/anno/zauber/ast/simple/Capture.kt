package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.MethodLike

data class Capture(val owner: MethodLike, val field: Field)
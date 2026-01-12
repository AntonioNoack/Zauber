package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.expression.Expression

class Catch(val param: Parameter, val body: Expression)
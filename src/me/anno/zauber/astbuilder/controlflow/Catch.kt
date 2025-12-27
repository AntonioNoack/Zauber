package me.anno.zauber.astbuilder.controlflow

import me.anno.zauber.astbuilder.Parameter
import me.anno.zauber.astbuilder.expression.Expression

class Catch(val param: Parameter, val handler: Expression)
package zauber

class Type {
    external fun isSubTypeOf(other: Type): Boolean
}
class ClassType<V> private constructor(): Type() {
    external val name: String
}
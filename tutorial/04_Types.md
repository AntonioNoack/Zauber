
## Types

Every instance has exactly one class type,
but functions can accept more than just one type.

For example, in Kotlin, any child class is also permitted.
In Zauber it is the same, but you are given much more freedom and power on how do define your times.

Complex types can be put into parenthesis, or you can create typealiases to make handling them easier.
Contrary to Kotlin, Zauber allows typealiases and imports in all scopes.

Type enhancements:
```kotlin
typealias SomeNullableType = Int? // either an Int or null
typealias SomeUnionType = Int | Long // either an Int or a Long
typealias SomeIntersectionType = Long & Int // Nothing can fulfill this
typealias SomeComplexType = Int? & !(Any?) // just Int, because it cannot be null
```

During type resolution, union types may occur naturally,
and then cause field/method-cannot-be-found errors.

There is also a few special types, two of which are new compared to Kotlin:

### Nothing Type
Nothing is a type, which cannot have instances.
If a field is declared with that type, that scope is considered unreachable.
Proper unreachable code should be marked explicitly though, if non-trivial.

### Self Type
Can be used on fields and methods and means, that that field needs to have the same class as what is being used.
This is useful for enforcing that a .clone() function returns an instance of itself.

It also is useful for "recursive"/"interlinked" types, e.g.
```kotlin
class A<Bi: A>
class B<Ai: B>
```
but now, since both classes have generics,
we must specify them... ugly in Kotlin, especially when inheriting from these types,
but in Zauber, we can say the following:
```kotlin
class A<Bi: B<Self>>
class B<Ai: A<Self>>
```


### This Type
Can be used for the builder pattern.
Only 'this' may be returned from a function returning 'This'.

For clone, you could even specify
```kotlin
fun clone(): Self&!This {
    ...
}
```
such that returning 'this' from .clone() becomes illegal. 


## Classes

Class names should be PascalCase.
There are the following class types:

```kotlin
// for any instances with (recommendation) roughly at most 1000 instances
class NormalClass

// for functionality-contracts between libraries
interface NormalInterface

// for automatic toString(), hashCode() and equals(); also for compatibility with Kotlin,
//  these methods are only considering the fields in the primary constructor
data class DataClass

// like data classes, but all fields must be declared in the primary constructor,
//  and all fields must be immutable
//  does not have a pointer-identity, so === will always return false
value class ValueClass

// data value classes or value data class make no sense,
//  and are just value classes

// for a small, fixed set of options;
//  cannot be extended by libraries;
//  automatically stores name and ordinal;
//  generates companion object, with val entries: List<SomeEnum> property
enum class SomeEnum {
    X,
    Y,
    Z
}

// sealed class are like Rust enums:
//  all their types are known at compile time,
//  so creating switch-cases for them is easier
sealed class SomeSealedClass
```

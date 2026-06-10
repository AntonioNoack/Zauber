
## Variables

Variables are either mutable (var) or immutable (val).
Mutability is shallow, but value classes always are fully immutable, so you can create deep immutable structures.

Method and constructor parameters are always immutable, but can easily be shadowed with a mutable version.

Variable names should be camelCase.

```kotlin
var someMutableVariable = 0
someMutableVariable++ // fine

val someImmutableVariable = 0f
someImmutableVariable++ // compiler error: Cannot mutate an immutable field.
```

The compiler will give its best (within reason) to automatically deduct the type of each field.
When it is not possible (buggy), or you want faster compile times, you can declare it yourself:

```kotlin
val someInt: Int = 0
val someOtherInt: Int = 0f // compiler error: Cannot assign Float to Int
```

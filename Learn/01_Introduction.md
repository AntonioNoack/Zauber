# Introduction

Zauber is a Kotlin-like language, sharing most of its syntax.
Any normal Kotlin code (<2.0?) shall be valid Zauber code.

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

Nothing is a type, which cannot have instances.
If a field is declared with that type, that scope is considered unreachable.
Proper unreachable code should be marked explicitly though, if non-trivial.

During type resolution, union types may occur naturally,
and then cause field/method-cannot-be-found errors.

## Imports

To get access to classes outside the standard library and other packages, you must import them.
(Just like in Kotlin)

Import statements can be placed in any scope.
They should be at the top of each scope though, below the package statement.

```kotlin
import some.sample.library
import everything.from.*
import numpy as np

fun main() {
    library.use()
}
```

The star operator (*) can be used to import all children of a scope. Unless you need ten or more, it's better to
list the imports explicitly to not shadow any other fields or imports.

Kotlin doesn't have the 'as' syntax to rename imports, but I like that, so Zauber supports it.

## Control Flow

Zauber inherits all usual suspects from Kotlin and the like.
It does not have goto. I'd like to add Zig's defer and errdefer some day (a cleaner way of try-finally/try-catch/Java-use-syntax).

```kotlin
if (someBool) {
    doOneThing()
} else {
    orAnother()
}

val ifStatementsAreExpressions = if(x > 5) x else x+1
// or at least if their else-branch is defined

// for statements have a field part,
//  and a value part, which must be an Iterable
// fun iterator() is called on the value, and hasNext()+next() until hasNext() returns false
for(i in 0 until 100) {
    println("$i")
    if(i > 50) break
    // optional, because we're at the end anyway
    continue
}

someLabel@ while(someConditionIsTrue) {
    executeSomeStuff()
    // break and continue are supported as well
    // you can give labels to repeating blocks to exit them early, or to enter the next iteration early
    break@someLabel
}

// do-while loops are supported, too,
do {
    someLogic()
    // and the condition operates in the scope of the body, so any field declared here can be used
} while(someCondition)

```

## Swap Operation

Swapping variables is a chore in Kotlin and Java.
Python has a nice syntax for it:
```python
(a,b) = (b,a+b)
```

so we're taking that, and using it in Zauber.

Rules:
- left and right side must have the same number of components
- the placeholder '_' is not supported, because it makes no sense here
- the left side must only be fields, the right side can be any expression
- all field assignments left to right must be compatible

You should keep expressions on the right compact to retain legibility.

## Chars & Strings

Just like in Kotlin, Strings have many useful methods attached to them,
but they have inherited cruft from Java, and Java from times before Emojis.

Their set of letters is always UTF-8, and they will be stored as such.
Access of 'chars' is deprecated.
The type 'Char' is deprecated, because there are more than 65k letters in Unicode.

## Getters and Setters

Fields may have custom getter and setter functions, and they will be executed when you read/write that field,
but please keep them simple.

```
var ctr = 0
    get() {
        println("Accessing ctr, value: $field") // calling 'ctr' here would result in a stack overflow by recursion
        return field
    }
    set(value) {
        // field is the underlying field; it only is available in the getter and setter scopes
        field = value
        println("Set ctr to $ctr") // prints the state, but invokes the getter; use 'field' to avoid that.
    }
val someSampleField
    get() = ctr++ // automatically calls getter and setter
val someFieldWithPrivateSetter
    private set // can only be set from within this scope (class/package/block...)
```

These getter/setter functions solve the discussion in Java:
Always just call the fields, but there may be some logic behind it where necessary.

## Unsafe Number Operations

I like safe algorithms, so like Zig,
and mathematical operations are limited to their safe (without overflow) ranges,
and any violation will throw an error.

zauber.math.UnsafeInt/UnsafeLong will define types, where overflow is well defined.
Maybe call them OverflowInt/OverflowLong?

I also like clamping float operations (forbidding Infinity and NaN), and disabling denormalized floats for performance,
but I wonder what the performance impact of the former is/how good hardware support is... .
Depending on the result, they will or will not be the default.



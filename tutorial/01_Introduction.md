# Introduction

Zauber is a Kotlin-like language, sharing most of its syntax.
Any normal Kotlin code (<2.0?) shall be valid Zauber code.

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

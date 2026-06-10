
## Chars & Strings

Just like in Kotlin, Strings have many useful methods attached to them,
but they have inherited cruft from Java, and Java from times before Emojis.

Their set of letters is always UTF-8, and they will be stored as such.
Access of 'chars' is deprecated.
The type 'Char' is deprecated, because there are more than 65k letters in Unicode.

These getter/setter functions solve the discussion in Java:
Always just call the fields, but there may be some logic behind it where necessary.

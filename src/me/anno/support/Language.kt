package me.anno.support

enum class Language {
    KOTLIN,
    ZAUBER,

    CSHARP,
    CPP, C,
    PYTHON,
    RUST,
    JAVA,
    TYPESCRIPT;

    val allowsDefaultsInParameterDeclaration: Boolean
        get() = this == KOTLIN || this == ZAUBER || this == CSHARP

    val allowsValuesAsTypes: Boolean
        get() = this == KOTLIN || this == ZAUBER || this == TYPESCRIPT

    companion object {
        fun byFileName(fileName: String): Language {
            return when {
                fileName.endsWith(".kt") || fileName.endsWith(".kts") -> KOTLIN
                fileName.endsWith(".java") -> JAVA
                fileName.endsWith(".cs") -> CSHARP
                fileName.endsWith(".rs") -> RUST
                else -> ZAUBER
            }
        }
    }
}
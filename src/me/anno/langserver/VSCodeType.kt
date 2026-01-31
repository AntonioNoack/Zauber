package me.anno.langserver

enum class VSCodeType(val code: String) {
    NAMESPACE("namespace"), // declare or reference a namespace, module, or package
    CLASS("class"), // declare or reference a class type
    ENUM("enum"), // declare or reference an enumeration type
    INTERFACE("interface"), // declare or reference an interface type
    STRUCT("struct"), // declare or reference a struct type
    TYPE_PARAM("typeParameter"), // declare or reference a type parameter
    TYPE("type"), // declare or reference a type that is not covered above
    PARAMETER("parameter"), // declare or reference a function or method parameters
    VARIABLE("variable"), // declare or reference a local or global variable
    PROPERTY("property"), // declare or reference a member property, member field, or member variable
    ENUM_MEMBER("enumMember"), // declare or reference an enumeration property, constant, or member
    DECORATOR("decorator"), // declare or reference decorators and annotations
    EVENT("event"), // declare an event property
    FUNCTION("function"), // declare a function
    METHOD("method"), // declare a member function or method
    MACRO("macro"), // declare a macro
    LABEL("label"), // declare a label
    COMMENT("comment"), // comment
    STRING("string"), // string literal
    KEYWORD("keyword"), // language keyword
    NUMBER("number"), // number literal
    REGEX("regexp"), // regular expression literal
    OPERATOR("operator"), // operator
}
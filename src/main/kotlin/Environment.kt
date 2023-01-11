class Environment {
    private val values: MutableMap<String, Any?> = mutableMapOf()

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun assign(name: Token, value: Any?) {
        if (name.lexeme in values) {
            define(name.lexeme, value)
        } else {
            throw RuntimeError(name, "Undefined variable '${name.lexeme}'")
        }
    }

    fun get(name: Token): Any? {
        if (name.lexeme !in values) {
            throw RuntimeError(name, "Undefined variable '${name.lexeme}'")
        }
        return values[name.lexeme]
    }
}
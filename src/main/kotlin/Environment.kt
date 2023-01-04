class Environment {
    private val values: MutableMap<String, Any?> = mutableMapOf()

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun get(name: Token): Any? {
        if (name.lexeme !in values) {
            throw RuntimeError(name, "Undefined variable '${name.lexeme}'")
        }
        return values[name.lexeme]
    }
}
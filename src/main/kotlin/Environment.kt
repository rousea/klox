class Environment(val enclosing: Environment? = null) {
    private val values: MutableMap<String, Any?> = mutableMapOf()

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun assign(name: Token, value: Any?) {
        if (name.lexeme in values) {
            define(name.lexeme, value)
        } else if (enclosing != null) {
            enclosing.assign(name, value)
        } else {
            throw RuntimeError(name, "Undefined variable '${name.lexeme}'")
        }
    }

    fun assignAt(distance: Int, name: Token, value: Any?) {
        ancestor(distance).values[name.lexeme] = value
    }

    fun get(name: Token): Any? {
        if (name.lexeme in values) {
            return values[name.lexeme]
        }

        return enclosing?.get(name) ?: throw RuntimeError(name, "Undefined variable '${name.lexeme}'")
    }

    fun getAt(distance: Int, name: String): Any? {
        return ancestor(distance).values[name]
    }

    private fun ancestor(distance: Int): Environment {
        var environment: Environment = this
        repeat(distance) {
            // trust that Resolver made sure this depth would be present
            environment = environment.enclosing!!
        }
        return environment
    }
}
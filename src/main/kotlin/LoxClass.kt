class LoxClass(
    val name: String,
    val superclass: LoxClass?,
    private val methods: Map<String, LoxFunction>
) : LoxCallable {
    override fun arity(): Int {
        return findMethod("init")?.arity() ?: 0
    }

    override fun call(interpreter: Interpreter, arguments: List<Any>): Any {
        val instance = LoxInstance(this)
        findMethod("init")?.bind(instance)?.call(interpreter, arguments)
        return instance
    }

    fun findMethod(name: String): LoxFunction? {
        return if (name in methods) {
            methods[name]
        } else {
            superclass?.findMethod(name)
        }
    }

    override fun toString() = name
}

class LoxInstance(private val klass: LoxClass) {
    private val fields = mutableMapOf<String, Any?>()

    fun get(name: Token): Any? {
        if (name.lexeme in fields) {
            return fields[name.lexeme]
        }

        val method = klass.findMethod(name.lexeme)
        if (method != null) return method.bind(this)

        throw RuntimeError(name, "Undefined property '${name.lexeme}'")
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString() = "${klass.name} instance"
}
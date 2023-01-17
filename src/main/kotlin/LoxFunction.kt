class LoxFunction(
    private val declaration: Stmt.Function,
    private val closure: Environment,
    private val isInitializer: Boolean,
) : LoxCallable {
    override fun arity() = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any>): Any? {
        val env = Environment(closure)

        declaration.params.forEachIndexed { i, param ->
            env.define(param.lexeme, arguments[i])
        }

        try {
            interpreter.executeBlock(declaration.body, env)
        } catch (retVal: Return) {
            return if (isInitializer) {
                closure.getAt(0, "this")
            } else {
                retVal.value
            }
        }

        if (isInitializer) return closure.getAt(0, "this")

        return null
    }

    fun bind(instance: LoxInstance): LoxFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return LoxFunction(declaration, environment, isInitializer)
    }

    override fun toString() = "<fn ${declaration.name.lexeme}>"
}
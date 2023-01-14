class LoxFunction(
    private val declaration: Stmt.Function
) : LoxCallable {
    override fun arity() = declaration.params.size

    override fun call(interpreter: Interpreter, arguments: List<Any>): Any? {
        val env = Environment(interpreter.globals)

        declaration.params.forEachIndexed { i, param ->
            env.define(param.lexeme, arguments[i])
        }

        try {
            interpreter.executeBlock(declaration.body, env)
        } catch (retVal: Return) {
            return retVal.value
        }
        return null
    }

    override fun toString() = "<fn ${declaration.name.lexeme}>"
}
class Interpreter : Expr.Visitor<Any?>, Stmt.Visitor<Unit> {
    val globals = Environment()
    private var environment = globals
    private val locals = mutableMapOf<Expr, Int>()

    init {
        globals.define("clock", object : LoxCallable {
            override fun arity() = 0

            override fun call(interpreter: Interpreter, arguments: List<Any>): Any {
                return System.currentTimeMillis() / 1000.0
            }

            override fun toString() = "<native fn>"
        })
    }

    fun interpret(stmts: List<Stmt>): Result<Int> {
        return try {
            stmts.forEach { execute(it) }
            Result.success(0)
        } catch (e: RuntimeError) {
            runtimeError(e)
            Result.failure(e)
        }
    }

    private fun execute(stmt: Stmt) {
        stmt.accept(this)
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    private fun stringify(any: Any?): String {
        return when (any) {
            null -> "nil"
            is Double -> {
                var text = any.toString()
                if (text.endsWith(".0")) {
                    text = text.substring(0, text.length - 2)
                }
                text
            }

            else -> any.toString()
        }
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        executeBlock(stmt.statements, Environment((environment)))
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        val superclass = if (stmt.superclass != null) {
            val c = evaluate(stmt.superclass)
            if (c !is LoxClass) {
                throw RuntimeError(stmt.superclass.name, "Superclass must be a class.")
            }
            c
        } else null


        environment.define(stmt.name.lexeme, null)

        if (stmt.superclass != null) {
            environment = Environment(environment)
            environment.define("super", superclass)
        }

        val methods = stmt.methods.associate { method ->
            method.name.lexeme to LoxFunction(method, environment, "init" == method.name.lexeme)
        }

        val klass = LoxClass(stmt.name.lexeme, superclass, methods)

        if (superclass != null) environment = environment.enclosing!!

        environment.assign(stmt.name, klass)
    }

    fun executeBlock(statements: List<Stmt>, env: Environment) {
        val prev = environment
        try {
            environment = env
            statements.forEach(this::execute)
        } finally {
            environment = prev
        }
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        val value = stmt.initializer?.let { expr ->
            evaluate(expr)
        }
        environment.define(stmt.name.lexeme, value)
    }

    override fun visitAssignExpr(expr: Expr.Assign): Any? {
        val value = evaluate(expr.value)
        val distance = locals[expr]
        if (distance != null) {
            environment.assignAt(distance, expr.name, value)
        } else {
            globals.assign(expr.name, value)
        }
        return value
    }

    override fun visitVariableExpr(expr: Expr.Variable): Any? {
        return lookUpVariable(expr.name, expr)
    }

    private fun lookUpVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        return if (distance != null) {
            environment.getAt(distance, name.lexeme)
        } else {
            globals.get(name)
        }
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        evaluate(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        val function = LoxFunction(stmt, environment, false)
        environment.define(stmt.name.lexeme, function)
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch)
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch)
        }
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        val value = evaluate(stmt.expression)
        println(stringify(value))
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        val value = stmt.value?.let {
            evaluate(it)
        }

        throw Return(value)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body)
        }
    }

    override fun visitBinaryExpr(expr: Expr.Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            TokenType.GREATER -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double > right as Double
            }

            TokenType.GREATER_EQUAL -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double >= right as Double
            }

            TokenType.LESS -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) < right as Double
            }

            TokenType.LESS_EQUAL -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double <= right as Double
            }

            TokenType.BANG_EQUALS -> isEqual(left, right)
            TokenType.EQUAL_EQUAL -> isEqual(left, right)
            TokenType.MINUS -> {
                checkNumberOperand(expr.operator, right)
                left as Double - right as Double
            }

            TokenType.SLASH -> {
                checkNumberOperands(expr.operator, left, right)
                val res = left as Double / right as Double
                checkDivideByZero(expr.operator, res)
                res
            }

            TokenType.STAR -> {
                checkNumberOperands(expr.operator, left, right)
                left as Double * right as Double
            }

            TokenType.PLUS -> {
                if (left is Double && right is Double) {
                    left + right
                } else if (left is String) {
                    left + (right?.toString() ?: "nil")
                } else if (right is String) {
                    (left?.toString() ?: "nil") + right
                } else {
                    throw RuntimeError(expr.operator, "Operands must be two numbers or two strings")
                }
            }

            else -> null
        }
    }

    override fun visitCallExpr(expr: Expr.Call): Any? {
        val callee = evaluate(expr.callee)

        val arguments = expr.arguments.mapNotNull { evaluate(it) }

        if (callee !is LoxCallable) {
            throw RuntimeError(expr.paren, "Can only call functions and classes.")
        } else if (arguments.size != callee.arity()) {
            throw RuntimeError(expr.paren, "Expected ${callee.arity()} arguments but got ${arguments.size}.")
        }

        return callee.call(this, arguments)
    }

    override fun visitGetExpr(expr: Expr.Get): Any? {
        val obj = evaluate(expr.obj)
        if (obj is LoxInstance) {
            return obj.get(expr.name)
        } else {
            throw RuntimeError(expr.name, "Only instances have properties.")
        }
    }

    override fun visitCommaExpr(expr: Expr.Comma): Any? {
        val exprs = expr.expressions
        val last = exprs.last()
        exprs.dropLast(1).forEach { e ->
            evaluate(e)
        }
        return evaluate(last)
    }

    override fun visitTernaryExpr(expr: Expr.Ternary): Any? {
        val res = evaluate(expr.condition)
        return if (isTruthy(res)) {
            evaluate(expr.thenBranch)
        } else {
            evaluate(expr.elseBranch)
        }
    }

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand !is Double) {
            throw RuntimeError(operator, "Operand must be a number.")
        }
    }

    private fun checkNumberOperands(operator: Token, left: Any?, right: Any?) {
        if (left !is Double && right !is Double) {
            throw RuntimeError(operator, "Operands must be a number.")
        }
    }

    private fun checkDivideByZero(operator: Token, result: Double) {
        when (result) {
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY -> {
                throw RuntimeError(operator, "Cannot divide by zero.")
            }
        }
    }

    private fun isEqual(left: Any?, right: Any?): Boolean {
        return if (left == null && right == null) {
            true
        } else left?.equals(right) ?: false
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): Any? {
        return evaluate(expr.expression)
    }

    override fun visitLiteralExpr(expr: Expr.Literal): Any? {
        return expr.value
    }

    override fun visitLogicalExpr(expr: Expr.Logical): Any? {
        val left = evaluate(expr.left)

        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) return left
        } else {
            if (!isTruthy(left)) return left
        }

        return evaluate(expr.right)
    }

    override fun visitSetExpr(expr: Expr.Set): Any? {
        val obj = evaluate(expr.obj)

        if (obj !is LoxInstance) {
            throw RuntimeError(expr.name, "Only instances have fields.")
        }

        val value = evaluate(expr.value)
        obj.set(expr.name, value)
        return value
    }

    override fun visitSuperExpr(expr: Expr.Super): Any {
        val distance = locals[expr]!!
        val superclass = environment.getAt(distance, "super") as LoxClass
        val obj = environment.getAt(distance - 1, "this") as LoxInstance
        val method = superclass.findMethod(expr.method.lexeme)

        if (method == null) {
            throw RuntimeError(expr.method, "Undefined property '${expr.method.lexeme}'.")
        }

        return method.bind(obj)
    }

    override fun visitThisExpr(expr: Expr.This): Any? {
        return lookUpVariable(expr.keyword, expr)
    }

    override fun visitUnaryExpr(expr: Expr.Unary): Any? {
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            TokenType.BANG -> !isTruthy(right)
            TokenType.MINUS -> -(right as Double)
            else -> null
        }
    }

    // only false and nil is 'falsey'
    private fun isTruthy(any: Any?): Boolean {
        if (any == null) return false
        if (any is Boolean) return any
        return true
    }

    private fun evaluate(expr: Expr) = expr.accept(this)
}
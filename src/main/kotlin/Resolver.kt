import java.util.*

class Resolver(
    private val interpreter: Interpreter
) : Expr.Visitor<Unit>, Stmt.Visitor<Unit> {
    private enum class FunctionType {
        NONE, FUNCTION, METHOD, INITIALIZER
    }

    private enum class ClassType {
        NONE, CLASS, SUBCLASS
    }

    // stack of scopes that marks variable as ready via declared vs defined
    private val scopes = Stack<MutableMap<String, Boolean>>()
    private var currentFunction = FunctionType.NONE
    private var currentClass = ClassType.NONE
    private var resolveError = false

    fun resolve(statements: List<Stmt>): Result<Unit> {
        statements.forEach { resolve(it) }
        return if (resolveError) {
            Result.failure(Throwable())
        } else {
            Result.success(Unit)
        }
    }

    private fun resolve(statement: Stmt) {
        statement.accept(this)
    }

    private fun resolve(expr: Expr) {
        expr.accept(this)
    }

    private fun resolveLocal(expr: Expr, name: Token) {
        scopes.asReversed().forEachIndexed { i, _ ->
            if (scopes[i].containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size - 1 - i)
                return
            }
        }
    }

    private fun beginScope() {
        scopes.push(mutableMapOf())
    }

    private fun endScope() {
        scopes.pop()
    }

    private fun declare(name: Token) {
        if (scopes.isNotEmpty()) {
            val scope = scopes.peek()

            if (name.lexeme in scope) {
                resolveError(name, "Already contains a variable named '${name.lexeme}' in this scope.")
            }

            scope[name.lexeme] = false
        }
    }

    private fun define(name: Token) {
        if (scopes.isNotEmpty()) {
            val scope = scopes.peek()
            scope[name.lexeme] = true
        }
    }

    override fun visitAssignExpr(expr: Expr.Assign) {
        resolve(expr.value)
        resolveLocal(expr, expr.name)
    }

    override fun visitBinaryExpr(expr: Expr.Binary) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitCallExpr(expr: Expr.Call) {
        resolve(expr.callee)
        expr.arguments.forEach { resolve(it) }
    }

    override fun visitGetExpr(expr: Expr.Get) {
        resolve(expr.obj)
    }

    override fun visitGroupingExpr(expr: Expr.Grouping) {
        resolve(expr.expression)
    }

    override fun visitLiteralExpr(expr: Expr.Literal) {
        // no variables, no expressions,  nothing to do
    }

    override fun visitLogicalExpr(expr: Expr.Logical) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitSetExpr(expr: Expr.Set) {
        resolve(expr.value)
        resolve(expr.obj)
    }

    override fun visitSuperExpr(expr: Expr.Super) {
        if (currentClass == ClassType.NONE) {
            resolveError(expr.keyword, "Can't use 'super' outside a class.")
        } else if (currentClass != ClassType.SUBCLASS) {
            resolveError(expr.keyword, "Can't use 'super' in a class with no superclass.")
        }

        resolveLocal(expr, expr.keyword)
    }

    override fun visitTernaryExpr(expr: Expr.Ternary) {
        resolve(expr.condition)
        resolve(expr.thenBranch)
        resolve(expr.elseBranch)
    }

    override fun visitThisExpr(expr: Expr.This) {
        if (currentClass == ClassType.NONE) {
            resolveError(expr.keyword, "Can't use 'this' outside of a class")
            return
        }
        resolveLocal(expr, expr.keyword)
    }

    override fun visitUnaryExpr(expr: Expr.Unary) {
        resolve(expr.right)
    }

    override fun visitVariableExpr(expr: Expr.Variable) {
        if (scopes.isNotEmpty() && scopes.peek()[expr.name.lexeme] == false) {
            resolveError(expr.name, "Can't read local variable in its initializer.")
        }

        resolveLocal(expr, expr.name)
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        beginScope()
        resolve(stmt.statements)
        endScope()
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        val enclosingClass = currentClass
        currentClass = ClassType.CLASS

        declare(stmt.name)
        define(stmt.name)

        if (stmt.superclass != null && stmt.name.lexeme == stmt.superclass.name.lexeme) {
            resolveError(stmt.superclass.name, "A class can't inherit from itself.")
        }

        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS
            resolve(stmt.superclass)
        }

        if (stmt.superclass != null) {
            beginScope()
            scopes.peek()["super"] = true
        }

        beginScope()
        scopes.peek()["this"] = true

        stmt.methods.forEach { method ->
            val type = if ("init" == method.name.lexeme) FunctionType.INITIALIZER else FunctionType.METHOD
            resolveFunction(method, type)
        }

        endScope()

        if (stmt.superclass != null) {
            endScope()
        }

        currentClass = enclosingClass
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        resolve(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        declare(stmt.name)
        define(stmt.name)

        resolveFunction(stmt, FunctionType.FUNCTION)
    }

    private fun resolveFunction(func: Stmt.Function, type: FunctionType) {
        val enclosingType = currentFunction
        currentFunction = type

        beginScope()
        func.params.forEach {
            declare(it)
            define(it)
        }
        resolve(func.body)
        endScope()

        currentFunction = enclosingType
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        resolve(stmt.condition)
        resolve(stmt.thenBranch)
        stmt.elseBranch?.let { resolve(it) }
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        resolve(stmt.expression)
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        if (currentFunction == FunctionType.NONE) {
            resolveError(stmt.keyword, "Can't return from top-level code.")
        }
        stmt.value?.let {
            if (currentFunction == FunctionType.INITIALIZER) {
                resolveError(stmt.keyword, "Can't return a value from an initializer.")
            }
            resolve(it)
        }
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        declare(stmt.name)
        stmt.initializer?.let { init ->
            resolve(init)
        }
        define(stmt.name)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        resolve(stmt.condition)
        resolve(stmt.body)
    }

    private fun resolveError(token: Token, msg: String) {
        resolveError = true
        error(token, msg)
    }
}
class Interpreter : Visitor<Any?> {
    fun interpret(expr: Expr): Result<Int> {
        return try {
            val value = evaluate(expr)
            println(stringify(value))
            Result.success(0)
        } catch (e: RuntimeError) {
            runtimeError(e)
            Result.failure(e)
        }
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
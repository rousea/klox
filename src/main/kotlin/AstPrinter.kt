class AstPrinter : Visitor<String> {
    fun print(expr: Expr): String {
        return expr.accept(this)
    }

    override fun visitBinaryExpr(expr: Expr.Binary): String {
        return paren(expr.operator.lexeme, expr.left, expr.right)
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): String {
        return paren("group", expr.expression)
    }

    override fun visitLiteralExpr(expr: Expr.Literal): String {
        return expr.value?.toString() ?: "nil"
    }

    override fun visitUnaryExpr(expr: Expr.Unary): String {
        return paren(expr.operator.lexeme, expr.right)
    }

    private fun paren(name: String, vararg exprs: Expr): String {
        return exprs.joinToString(prefix = "($name ", separator = " ", postfix = ")") { expr ->
            expr.accept(this)
        }
    }
}
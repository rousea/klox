interface Visitor<R> {
    fun visitBinaryExpr(expr: Expr.Binary): R
    fun visitGroupingExpr(expr: Expr.Grouping): R
    fun visitLiteralExpr(expr: Expr.Literal): R
    fun visitUnaryExpr(expr: Expr.Unary): R
}

sealed class Expr {
  abstract fun <R> accept(visitor: Visitor<R>): R
  data class Binary(
    val left: Expr,
    val operator: Token,
    val right: Expr,
  ): Expr() {
    override fun <R> accept(visitor: Visitor<R>): R {
      return visitor.visitBinaryExpr(this)
    }
  }
  data class Grouping(
    val expression: Expr,
  ): Expr() {
    override fun <R> accept(visitor: Visitor<R>): R {
      return visitor.visitGroupingExpr(this)
    }
  }
  data class Literal(
    val value: Any?,
  ): Expr() {
    override fun <R> accept(visitor: Visitor<R>): R {
      return visitor.visitLiteralExpr(this)
    }
  }
  data class Unary(
    val operator: Token,
    val right: Expr,
  ): Expr() {
    override fun <R> accept(visitor: Visitor<R>): R {
      return visitor.visitUnaryExpr(this)
    }
  }
}

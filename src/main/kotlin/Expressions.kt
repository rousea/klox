sealed class Expression {
    class Binary(
        private val left: Expression,
        private val operator: Token,
        private val right: Expression
    ): Expression()
}
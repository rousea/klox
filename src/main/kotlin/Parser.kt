import TokenType.*

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): Result<Expr> {
        return try {
            val expr = expression()
            Result.success(expr)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun expression() = equality()

    private fun equality(): Expr {
        var expr = comparison()

        while (match(BANG_EQUALS, EQUAL_EQUAL)) {
            val operator = previous()
            val right = comparison()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun comparison() = binary(this::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)

    private fun term() = binary(this::factor, PLUS, MINUS)

    private fun factor() = binary(this::unary, SLASH, STAR)

    private fun unary(): Expr {
        if (match(BANG, MINUS)) {
            val operator = previous()
            val right = unary()
            return Expr.Unary(operator, right)
        }
        return primary()
    }

    private fun primary(): Expr {
        if (match(FALSE)) return Expr.Literal(false)
        if (match(TRUE)) return Expr.Literal(true)
        if (match(NIL)) return Expr.Literal(null)

        if (match(NUMBER, STRING)) {
            return Expr.Literal(previous().literal)
        }

        if (match(LEFT_PAREN)) {
            val expr = expression()
            consume(RIGHT_PAREN, "Expect ')' after expression.")
            return Expr.Grouping(expr)
        }

        throw _error(peek(), "Expect expression.")
    }

    private fun binary(operand: () -> Expr, vararg types: TokenType): Expr {
        var expr = operand()

        while (match(*types)) {
            val operator = previous()
            val right = operand()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun match(vararg types: TokenType): Boolean {
        return types.any { type ->
            if (check(type)) {
                advance()
                true
            } else {
                false
            }
        }
    }

    private fun consume(type: TokenType, msg: String): Token {
        if (check(type)) return advance()
        throw _error(peek(), msg)
    }

    private fun _error(token: Token, msg: String): Exception {
        error(token, msg)
        return RuntimeException()
    }

    private fun synchronize() {
        advance()

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return

            when (peek().type) {
                CLASS, FUN, VAR, FOR, IF, WHILE, PRINT, RETURN -> return
                else -> {}
            }

            advance()
        }
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd() = peek().type == EOF

    private fun peek() = tokens[current]

    private fun previous() = tokens[current - 1]
}
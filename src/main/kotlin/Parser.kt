import TokenType.*

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): Result<List<Stmt>> {
        val list: MutableList<Stmt> = mutableListOf()
        while (!isAtEnd()) {
            val r = declaration()
            if (r.isSuccess) {
                list.add(r.getOrThrow())
            } else {
                return Result.failure(r.exceptionOrNull()!!)
            }
        }
        return Result.success(list)
    }

    private fun declaration(): Result<Stmt> {
        return try {
            if (match(VAR)) {
                Result.success(varDeclaration())
            } else {
                Result.success(statement())
            }
        } catch (e: ParseError) {
            synchronize()
            Result.failure(e)
        }
    }

    private fun varDeclaration(): Stmt {
        val name = consume(IDENTIFIER, "Expect variable name.")

        var initializer: Expr? = null;
        if (match(EQUAL)) {
            initializer = expression()
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.")
        return Stmt.Var(name, initializer)
    }

    private fun expression() = assignment()

    private fun statement(): Stmt {
        return if (match(IF)) {
            ifStatement()
        } else if (match(PRINT)) {
            printStatement()
        } else if (match(WHILE)) {
            whileStatement()
        } else if (match(LEFT_BRACE)) {
            Stmt.Block(block())
        } else {
            expressionStatement()
        }
    }

    private fun ifStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after condition.")

        val thenBranch = statement()
        val elseBranch = if (match(ELSE)) {
            statement()
        } else {
            null
        }
        return Stmt.If(condition, thenBranch, elseBranch)
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(SEMICOLON, "Expect ';' after value")
        return Stmt.Print(value)
    }

    private fun whileStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'while'")
        val condition = expression()
        consume(RIGHT_PAREN, "Expect ')' after while condition")
        val body = statement()

        return Stmt.While(condition, body)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(SEMICOLON, "Expect ';' after expression")
        return Stmt.Expression(expr)
    }

    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration().getOrThrow())
        }

        consume(RIGHT_BRACE, "Expect '}' after block.")
        return statements
    }

    private fun assignment(): Expr {
        val expr = or()

        if (match(EQUAL)) {
            val equals = previous()
            val value = assignment()

            if (expr is Expr.Variable) {
                return Expr.Assign(expr.name, value)
            }

            error(equals, "Invalid assignment target.")
        }

        return expr
    }

    private fun or(): Expr {
        var expr = and()

        if (match(OR)) {
            val operator = previous()
            val right = and()
            expr = Expr.Logical(expr, operator, right)
        }

        return expr
    }

    private fun and(): Expr {
        var expr = equality()

        if (match(AND)) {
            val operator = previous()
            val right = equality()
            expr = Expr.Logical(expr, operator, right)
        }

        return expr
    }

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

        if (match(IDENTIFIER)) {
            return Expr.Variable(previous())
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

    private fun _error(token: Token, msg: String): ParseError {
        error(token, msg)
        return ParseError()
    }

    private class ParseError() : RuntimeException()

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
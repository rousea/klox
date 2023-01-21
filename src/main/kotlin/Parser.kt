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
            if (match(CLASS)) {
                Result.success(classDeclaration())
            } else if (match(FUN)) {
                Result.success(function("function"))
            } else if (match(VAR)) {
                Result.success(varDeclaration())
            } else {
                Result.success(statement())
            }
        } catch (e: ParseError) {
            synchronize()
            Result.failure(e)
        }
    }

    private fun classDeclaration(): Stmt.Class {
        val name = consume(IDENTIFIER, "Expect class name.")

        val superclass = if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.")
            Expr.Variable(previous())
        } else null

        consume(LEFT_BRACE, "Expect '{' before class body.")

        val methods = mutableListOf<Stmt.Function>()
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            methods.add(function("method"))
        }

        consume(RIGHT_BRACE, "Expect '}' after class body.")
        return Stmt.Class(name, superclass, methods)
    }

    private fun function(kind: String): Stmt.Function {
        val name = consume(IDENTIFIER, "Expect $kind name.")
        consume(LEFT_PAREN, "Expect '(' after $kind name.")
        val parameters = mutableListOf<Token>()

        // check for zero parameters
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size >= 255) {
                    error(peek(), "Can't have more than 255 parameters.")
                }

                parameters.add(consume(IDENTIFIER, "Expect parameter name."))
            } while (match(COMMA))
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.")

        consume(LEFT_BRACE, "Expect '{' before $kind body.")
        val body = block()
        return Stmt.Function(name, parameters, body)
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
        return if (match(FOR)) {
            forStatement()
        } else if (match(IF)) {
            ifStatement()
        } else if (match(PRINT)) {
            printStatement()
        } else if (match(RETURN)) {
            returnStatement()
        } else if (match(WHILE)) {
            whileStatement()
        } else if (match(LEFT_BRACE)) {
            Stmt.Block(block())
        } else {
            expressionStatement()
        }
    }

    private fun forStatement(): Stmt {
        consume(LEFT_PAREN, "Expect '(' after 'for'.")

        val initializer = if (match(SEMICOLON)) {
            null
        } else if (match(VAR)) {
            varDeclaration()
        } else {
            expressionStatement()
        }

        val condition = if (!check(SEMICOLON)) {
            expression()
        } else {
            null
        }

        consume(SEMICOLON, "Expect ';' after loop condition.")

        val increment = if (!check(RIGHT_PAREN)) {
            expression()
        } else {
            null
        }

        consume(RIGHT_PAREN, "Expect ')' after for clauses.")

        var body: Stmt = statement()

        increment?.let {
            body = Stmt.Block(listOf(body, Stmt.Expression(it)))
        }

        body = if (condition == null) {
            Stmt.While(Expr.Literal(true), body)
        } else {
            Stmt.While(condition, body)
        }

        initializer?.let {
            body = Stmt.Block(listOf(it, body))
        }

        return body
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

    private fun returnStatement(): Stmt {
        val keyword = previous()
        val value = if (!check(SEMICOLON)) {
            expression()
        } else {
            null
        }

        consume(SEMICOLON, "Expect ';' after return value")
        return Stmt.Return(keyword, value)
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
            } else if (expr is Expr.Get) {
                return Expr.Set(expr.obj, expr.name, value)
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
        return call()
    }

    private fun call(): Expr {
        var expr = primary()

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr)
            } else if (match(DOT)) {
                val name = consume(IDENTIFIER, "Expect property name after '.'.")
                expr = Expr.Get(expr, name)
            } else {
                break;
            }
        }

        return expr
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = mutableListOf<Expr>()
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size >= 255) {
                    error(peek(), "Can't have more than 255 arguments.")
                }

                arguments.add(expression())
            } while (match(COMMA))
        }

        val paren = consume(RIGHT_PAREN, "Expect ')' after arguments.")

        return Expr.Call(callee, paren, arguments)
    }

    private fun primary(): Expr {
        if (match(FALSE)) return Expr.Literal(false)
        if (match(TRUE)) return Expr.Literal(true)
        if (match(NIL)) return Expr.Literal(null)

        if (match(NUMBER, STRING)) {
            return Expr.Literal(previous().literal)
        }

        if (match(SUPER)) {
            val keyword = previous()
            consume(DOT, "Expect '.' after 'super'.")
            val method = consume(IDENTIFIER, "Expect superclass method name.")
            return Expr.Super(keyword, method)
        }

        if (match(THIS)) return Expr.This(previous())

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
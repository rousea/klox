import TokenType.*

class Scanner(private val source: String) {
    private val keywords = mapOf(
        "and" to AND,
        "class" to CLASS,
        "else" to ELSE,
        "false" to FALSE,
        "for" to FOR,
        "fun" to FUN,
        "if" to IF,
        "nil" to NIL,
        "or" to OR,
        "print" to PRINT,
        "return" to RETURN,
        "super" to SUPER,
        "this" to THIS,
        "true" to TRUE,
        "var" to VAR,
        "while" to WHILE,
    )

    private val tokens = mutableListOf<Token>()

    private var start = 0;
    private var current = 0;
    private var line = 1;

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            // we are at the beginning of the next lexeme
            start = current
            scanToken()
        }

        tokens.add(Token(EOF, "", null, line))
        return tokens
    }

    private fun scanToken() {
        when (val c = advance()) {
            '(' -> addToken(LEFT_PAREN)
            ')' -> addToken(RIGHT_PAREN)
            '{' -> addToken(LEFT_BRACE)
            '}' -> addToken(RIGHT_BRACE)
            ',' -> addToken(COMMA)
            '.' -> addToken(DOT)
            '-' -> addToken(MINUS)
            '+' -> addToken(PLUS)
            ';' -> addToken(SEMICOLON)
            '*' -> addToken(STAR)
            '!' -> if (match('=')) addToken(BANG_EQUALS) else addToken(BANG)
            '=' -> if (match('=')) addToken(EQUAL_EQUAL) else addToken(EQUAL)
            '<' -> if (match('=')) addToken(LESS_EQUAL) else addToken(LESS)
            '>' -> if (match('=')) addToken(GREATER_EQUAL) else addToken(GREATER)
            '/' -> {
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance()
                } else if (match('*')) {
                    blockComment()
                } else {
                    addToken(SLASH)
                }
            }

            ' ', '\r', '\t' -> {} /* do nothing */

            '\n' -> line++

            '"' -> string()

            else -> {
                if (isDigit(c)) {
                    number()
                } else if (isAlpha(c)) {
                    identifier()
                } else {
                    error(line, "Unexpected character.")
                }
            }
        }
    }

    private fun addToken(type: TokenType, literal: Any? = null) =
        tokens.add(Token(type, source.substring(start, current), literal, line))

    private fun advance() = source[current++]

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        current++
        return true
    }

    // single char lookahead
    private fun peek() = if (isAtEnd()) 0.toChar() else source[current]

    // lookahead 2 chars
    private fun peekNext(): Char {
        return if (current + 1 >= source.length) {
            0.toChar()
        } else {
            source[current + 1]
        }
    }

    private fun isAtEnd() = current >= source.length

    private fun isDigit(c: Char) = c in '0'..'9'

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }

        if (isAtEnd()) {
            error(line, "Unterminated string.")
            return
        }

        // closing "
        advance()

        // trim surrounding quotes
        addToken(STRING, source.substring(start + 1, current - 1))
    }

    private fun number() {
        while (isDigit(peek())) advance()

        if (peek() == '.' && isDigit(peekNext())) {
            // consume the "."
            advance()

            while (isDigit(peek())) advance()
        }

        addToken(NUMBER, source.substring(start, current).toDouble())
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) advance()

        // distinguish between reserved words and regular identifiers
        val text = source.substring(start, current)
        addToken(keywords[text] ?: IDENTIFIER)
    }

    private fun isAlpha(c: Char) = c in 'a'..'z' || c in 'A'..'Z' || c == '_'

    private fun isAlphaNumeric(c: Char) = isAlpha(c) || isDigit(c)

    private fun blockComment() {
        while (peek() != '*' && peekNext() != '/' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }

        if (isAtEnd()) {
            error(line, "Unterminated comment.")
            return
        }

        // consume *
        advance()

        // consume /
        advance()
    }
}
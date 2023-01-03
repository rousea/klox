import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

private val interpreter = Interpreter()

private enum class Result(val exitCode: Int) {
    SUCCESS(0),
    PARSE_ERROR(65),
    RUNTIME_ERROR(70)
}

private fun runFile(path: String) {
    val bytes = Files.readAllBytes(Paths.get(path))
    val result = run(String(bytes))
    if (result != Result.SUCCESS) {
        exitProcess(result.exitCode)
    }
}

private fun runPrompt() {
    val reader = System.`in`.bufferedReader()
    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line)
    }
}

private fun run(source: String): Result {
    val tokens = Scanner(source).scanTokens()
    val expr = Parser(tokens).parse()

    if (expr.isFailure) {
        return Result.PARSE_ERROR
    }

    val res = interpreter.interpret(expr.getOrThrow())
    if (res.isFailure) {
        return Result.RUNTIME_ERROR
    }

    return Result.SUCCESS
}

fun error(token: Token, msg: String) {
    if (token.type == TokenType.EOF) {
        report(token.line, "at end", msg)
    } else {
        report(token.line, "at '${token.lexeme}'", msg)
    }
}

fun error(line: Int, msg: String) {
    report(line, "", msg)
}

fun runtimeError(error: RuntimeError) {
    System.err.println("${error.message}\n[line ${error.token.line}]")
}

fun report(line: Int, _where: String, msg: String) {
    System.err.println("[line $line] Error $_where: $msg")
}

fun main(args: Array<String>) {
    if (args.size > 1) {
        println("Usage: klox [script]")
        exitProcess(64)
    } else if (args.size == 1) {
        runFile(args[0])
    } else {
        runPrompt()
    }
}
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess

private var hadError = false

private fun runFile(path: String) {
    val bytes = Files.readAllBytes(Paths.get(path))
    run (String(bytes))
    if (hadError) exitProcess(65)
}

private fun runPrompt() {
    val reader = System.`in`.bufferedReader()
    while (true) {
        print("> ")
        val line = reader.readLine() ?: break
        run(line)
        hadError = false
    }
}

private fun run(source: String) {
    Scanner(source).scanTokens().forEach { token ->
        println(token)
    }
}

fun error(line: Int, msg: String) {
    report(line, "", msg)
}

fun report(line: Int, _where: String, msg: String) {
    System.err.println("[line $line] Error $_where: $msg")
    hadError = true
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
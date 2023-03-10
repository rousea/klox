package tools

import java.io.File
import java.io.PrintWriter
import kotlin.system.exitProcess

private fun PrintWriter.defineType(baseName: String, className: String, fields: String) {
    println("  data class $className(")
    // constructor
    fields.split(", ").forEach { field ->
        println("    val $field,")
    }
    println("  ): $baseName() {")
    println("    override fun <R> accept(visitor: Visitor<R>): R {")
    println("      return visitor.visit$className$baseName(this)")
    println("    }")
    println("  }")
}

private fun defineAst(outputDir: String, baseName: String, types: List<String>) {
    File("$outputDir/$baseName.kt").printWriter().use { writer ->
        with(writer) {
            println("sealed class $baseName {")
            println("  interface Visitor<R> {")
            types.forEach { type ->
                val typeName = type.split(":", limit = 2)[0].trim()
                println("    fun visit$typeName$baseName(${baseName.lowercase()}: $typeName): R")
            }
            println("  }")

            println("  abstract fun <R> accept(visitor: Visitor<R>): R")

            // AST classes
            types.forEach { type ->
                val split = type.split(":", limit = 2)
                val className = split[0].trim()
                val fields = split[1].trim()
                defineType(baseName, className, fields)
            }

            println("}")
        }
    }
}

fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("Usage: generate_ast <output_dir>")
        exitProcess(64)
    }

    val outputDir = args[0]

    // expressions
    defineAst(
        outputDir, "Expr", listOf(
            "Comma    : expressions: List<Expr>",
            "Assign   : name: Token, value: Expr",
            "Binary   : left: Expr, operator: Token, right: Expr",
            "Call     : callee: Expr, paren: Token, arguments: List<Expr>",
            "Get      : obj: Expr, name: Token",
            "Grouping : expression: Expr",
            "Literal  : value: Any?",
            "Ternary  : condition: Expr, thenBranch: Expr, elseBranch: Expr",
            "Logical  : left: Expr, operator: Token, right: Expr",
            "Set      : obj: Expr, name: Token, value: Expr",
            "Super    : keyword: Token, method: Token",
            "This     : keyword: Token",
            "Unary    : operator: Token, right: Expr",
            "Variable : name: Token",
        )
    )

    // statements
    defineAst(
        outputDir, "Stmt", listOf(
            "Block      : statements: List<Stmt>",
            "Class      : name: Token, superclass: Expr.Variable?, methods: List<Stmt.Function>",
            "Expression : expression: Expr",
            "Function   : name: Token, params: List<Token>, body: List<Stmt>",
            "If         : condition: Expr, thenBranch: Stmt, elseBranch: Stmt?",
            "Print      : expression: Expr",
            "Return     : keyword: Token, value: Expr?",
            "Var        : name: Token, initializer: Expr?",
            "While      : condition: Expr, body: Stmt",
        )
    )
}
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
            "Assign   : name: Token, value: Expr",
            "Binary   : left: Expr, operator: Token, right: Expr",
            "Grouping : expression: Expr",
            "Literal  : value: Any?",
            "Unary    : operator: Token, right: Expr",
            "Variable : name: Token",
        )
    )

    // statements
    defineAst(
        outputDir, "Stmt", listOf(
            "Block      : statements: List<Stmt>",
            "Expression : expression: Expr",
            "If         : condition: Expr, thenBranch: Stmt, elseBranch: Stmt?",
            "Print      : expression: Expr",
            "Var        : name: Token, initializer: Expr?",
        )
    )
}
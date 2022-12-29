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
            println("interface Visitor<R> {")
            types.forEach { type ->
                val typeName = type.split(":", limit = 2)[0].trim()
                println("    fun visit$typeName$baseName(${baseName.lowercase()}: $baseName.$typeName): R")
            }
            println("}")
            println()

            println("sealed class $baseName {")

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

    defineAst(
        outputDir, "Expr", listOf(
            "Binary   : left: Expr, operator: Token, right: Expr",
            "Grouping : expression: Expr",
            "Literal  : value: Any?",
            "Unary    : operator: Token, right: Expr"
        )
    )
}
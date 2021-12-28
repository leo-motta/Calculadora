package com.example.calculadorasimplesteste

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.*
import kotlin.math.pow


internal class Token(val type: TokenType,
                     val lexeme: String,
                     val literal: Any?) {

    override fun toString(): String {
        return "$type $lexeme $literal"
    }

}

internal enum class TokenType {

    // Basic operators
    PLUS,
    MINUS,
    STAR,
    SLASH,
    MODULO,
    EXPONENT,
    ASSIGN,

    // Logical operators
    EQUAL_EQUAL,
    NOT_EQUAL,
    GREATER,
    GREATER_EQUAL,
    LESS,
    LESS_EQUAL,
    BAR_BAR,
    AMP_AMP,

    // Other
    COMMA,

    // Parentheses
    LEFT_PAREN,
    RIGHT_PAREN,

    // Literals
    NUMBER,
    IDENTIFIER,

    EOF

}

class ExpressionException(message: String)
    : RuntimeException(message)

@Suppress("unused")
class Expressions {
    private val evaluator = Evaluator()

    init {
        define("pi", Math.PI)
        define("e", Math.E)

        evaluator.addFunction("abs", object : Function() {
            override fun call(arguments: List<BigDecimal>): BigDecimal {
                if (arguments.size != 1) throw ExpressionException(
                        "abs requires one argument")

                return arguments.first().abs()
            }
        })

        evaluator.addFunction("sum", object : Function() {
            override fun call(arguments: List<BigDecimal>): BigDecimal {
                if (arguments.isEmpty()) throw ExpressionException(
                        "sum requires at least one argument")

                return arguments.reduce { sum, bigDecimal ->
                    sum.add(bigDecimal)
                }
            }
        })

        evaluator.addFunction("floor", object : Function() {
            override fun call(arguments: List<BigDecimal>): BigDecimal {
                if (arguments.size != 1) throw ExpressionException(
                        "abs requires one argument")

                return arguments.first().setScale(0, RoundingMode.FLOOR)
            }
        })

        evaluator.addFunction("ceil", object : Function() {
            override fun call(arguments: List<BigDecimal>): BigDecimal {
                if (arguments.size != 1) throw ExpressionException(
                        "abs requires one argument")

                return arguments.first().setScale(0, RoundingMode.CEILING)
            }
        })

        evaluator.addFunction("round", object : Function() {
            override fun call(arguments: List<BigDecimal>): BigDecimal {
                if (arguments.size !in listOf(1, 2)) throw ExpressionException(
                        "round requires either one or two arguments")

                val value = arguments.first()
                val scale = if (arguments.size == 2) arguments.last().toInt() else 0

                return value.setScale(scale, roundingMode)
            }
        })

        evaluator.addFunction("min", object : Function() {
            override fun call(arguments: List<BigDecimal>): BigDecimal {
                if (arguments.isEmpty()) throw ExpressionException(
                        "min requires at least one argument")

                return arguments.minOrNull()!!
            }
        })

        evaluator.addFunction("max", object : Function() {
            override fun call(arguments: List<BigDecimal>): BigDecimal {
                if (arguments.isEmpty()) throw ExpressionException(
                        "max requires at least one argument")

                return arguments.maxOrNull()!!
            }
        })

        evaluator.addFunction("if", object : Function() {
            override fun call(arguments: List<BigDecimal>): BigDecimal {
                val condition = arguments[0]
                val thenValue = arguments[1]
                val elseValue = arguments[2]

                return if (condition != BigDecimal.ZERO) {
                    thenValue
                } else {
                    elseValue
                }
            }
        })
    }

    private val precision: Int
        get() = evaluator.mathContext.precision

    val roundingMode: RoundingMode
        get() = evaluator.mathContext.roundingMode

    fun setPrecision(precision: Int): Expressions {
        evaluator.mathContext = MathContext(precision, roundingMode)


        return this
    }

    fun setRoundingMode(roundingMode: RoundingMode): Expressions {
        evaluator.mathContext = MathContext(precision, roundingMode)

        return this
    }

    fun define(name: String, value: Long): Expressions {
        define(name, value.toString())

        return this
    }

    private fun define(name: String, value: Double): Expressions {
        define(name, value.toString())

        return this
    }

    fun define(name: String, value: BigDecimal): Expressions {
        define(name, value.toPlainString())

        return this
    }

    private fun define(name: String, expression: String): Expressions {
        val expr = parse(expression)
        evaluator.define(name, expr)

        return this
    }

    fun addFunction(name: String, function: Function): Expressions {
        evaluator.addFunction(name, function)

        return this
    }

    fun addFunction(name: String, func: (List<BigDecimal>) -> BigDecimal): Expressions {
        evaluator.addFunction(name, object : Function() {
            override fun call(arguments: List<BigDecimal>): BigDecimal {
                return func(arguments)
            }

        })

        return this
    }

    fun eval(expression: String): BigDecimal {
        return evaluator.eval(parse(expression))
    }

    /**
     * eval an expression then round it with {@link Evaluator#mathContext} and call toEngineeringString <br>
     * if error will return message from Throwable
     * @param expression String
     * @return String
     */
    fun evalToString(expression: String): String {
        return try {
            evaluator.eval(parse(expression)).round(evaluator.mathContext).stripTrailingZeros()
                    .toEngineeringString()
        }catch (e:Throwable){
            e.cause?.message ?: e.message ?: "unknown error"
        }
    }

    private fun parse(expression: String): Expr {
        return parse(scan(expression))
    }

    private fun parse(tokens: List<Token>): Expr {
        return Parser(tokens).parse()
    }

    private fun scan(expression: String): List<Token> {
        return Scanner(expression, evaluator.mathContext).scanTokens()
    }

}

internal class Evaluator : ExprVisitor<BigDecimal> {
    internal var mathContext: MathContext = MathContext.DECIMAL64

    private val variables: LinkedHashMap<String, BigDecimal> = linkedMapOf()
    private val functions: MutableMap<String, Function> = mutableMapOf()

    private fun define(name: String, value: BigDecimal) {
        variables += name to value
    }

    fun define(name: String, expr: Expr): Evaluator {
        define(name.toLowerCase(Locale.ROOT), eval(expr))

        return this
    }

    fun addFunction(name: String, function: Function): Evaluator {
        functions += name.toLowerCase(Locale.ROOT) to function

        return this
    }

    fun eval(expr: Expr): BigDecimal {
        return expr.accept(this)
    }

    override fun visitAssignExpr(expr: AssignExpr): BigDecimal {
        val value = eval(expr.value)

        define(expr.name.lexeme, value)

        return value
    }

    override fun visitLogicalExpr(expr: LogicalExpr): BigDecimal {
        val left = expr.left
        val right = expr.right

        return when (expr.operator.type) {
            TokenType.BAR_BAR -> left or right
            TokenType.AMP_AMP -> left and right
            else -> throw ExpressionException(
                    "Invalid logical operator '${expr.operator.lexeme}'")
        }
    }

    override fun visitBinaryExpr(expr: BinaryExpr): BigDecimal {
        val left = eval(expr.left)
        val right = eval(expr.right)

        return when (expr.operator.type) {
            TokenType.PLUS -> left + right
            TokenType.MINUS -> left - right
            TokenType.STAR -> left * right
            TokenType.SLASH -> left.divide(right, mathContext)
            TokenType.MODULO -> left.remainder(right, mathContext)
            TokenType.EXPONENT -> left pow right
            TokenType.EQUAL_EQUAL -> (left == right).toBigDecimal()
            TokenType.NOT_EQUAL -> (left != right).toBigDecimal()
            TokenType.GREATER -> (left > right).toBigDecimal()
            TokenType.GREATER_EQUAL -> (left >= right).toBigDecimal()
            TokenType.LESS -> (left < right).toBigDecimal()
            TokenType.LESS_EQUAL -> (left <= right).toBigDecimal()
            else -> throw ExpressionException(
                    "Invalid binary operator '${expr.operator.lexeme}'")
        }
    }

    override fun visitUnaryExpr(expr: UnaryExpr): BigDecimal {
        val right = eval(expr.right)

        return when (expr.operator.type) {
            TokenType.MINUS -> {
                right.negate()
            }
            else -> throw ExpressionException("Invalid unary operator")
        }
    }

    override fun visitCallExpr(expr: CallExpr): BigDecimal {
        val name = expr.name
        val function = functions[name.toLowerCase(Locale.ROOT)]
                ?: throw ExpressionException("Undefined function '$name'")

        return function.call(expr.arguments.map { eval(it) })
    }

    override fun visitLiteralExpr(expr: LiteralExpr): BigDecimal {
        return expr.value
    }

    override fun visitVariableExpr(expr: VariableExpr): BigDecimal {
        val name = expr.name.lexeme

        return variables[name.toLowerCase(Locale.ROOT)] ?:
        throw ExpressionException("Undefined variable '$name'")
    }

    override fun visitGroupingExpr(expr: GroupingExpr): BigDecimal {
        return eval(expr.expression)
    }

    private infix fun Expr.or(right: Expr): BigDecimal {
        val left = eval(this)

        // short-circuit if left is truthy
        if (left.isTruthy()) return BigDecimal.ONE

        return eval(right).isTruthy().toBigDecimal()
    }

    private infix fun Expr.and(right: Expr): BigDecimal {
        val left = eval(this)

        // short-circuit if left is falsey
        if (!left.isTruthy()) return BigDecimal.ZERO

        return eval(right).isTruthy().toBigDecimal()
    }

    private fun BigDecimal.isTruthy(): Boolean {
        return this != BigDecimal.ZERO
    }

    private fun Boolean.toBigDecimal(): BigDecimal {
        return if (this) BigDecimal.ONE else BigDecimal.ZERO
    }

    private infix fun BigDecimal.pow(n: BigDecimal): BigDecimal {
        var right = n
        val signOfRight = right.signum()
        right = right.multiply(signOfRight.toBigDecimal())
        val remainderOfRight = right.remainder(BigDecimal.ONE)
        val n2IntPart = right.subtract(remainderOfRight)
        val intPow = pow(n2IntPart.intValueExact(), mathContext)
        val doublePow = BigDecimal(toDouble().pow(remainderOfRight.toDouble()))

        var result = intPow.multiply(doublePow, mathContext)
        if (signOfRight == -1) result = BigDecimal
                .ONE.divide(result, mathContext.precision, RoundingMode.HALF_UP)

        return result
    }

}


internal sealed class Expr {

    abstract fun <R> accept(visitor: ExprVisitor<R>): R

}

internal class AssignExpr(val name: Token,
                          val value: Expr) : Expr() {

    override fun <R> accept(visitor: ExprVisitor<R>): R {
        return visitor.visitAssignExpr(this)
    }

}

internal class LogicalExpr(val left: Expr,
                           val operator: Token,
                           val right: Expr) : Expr() {

    override fun <R> accept(visitor: ExprVisitor<R>): R {
        return visitor.visitLogicalExpr(this)
    }

}

internal class BinaryExpr(val left: Expr,
                          val operator: Token,
                          val right: Expr) : Expr() {

    override fun <R> accept(visitor: ExprVisitor<R>): R {
        return visitor.visitBinaryExpr(this)
    }

}

internal class UnaryExpr(val operator: Token,
                         val right: Expr) : Expr() {

    override fun <R> accept(visitor: ExprVisitor<R>): R {
        return visitor.visitUnaryExpr(this)
    }

}

internal class CallExpr(val name: String,
                        val arguments: List<Expr>) : Expr() {

    override fun <R> accept(visitor: ExprVisitor<R>): R {
        return visitor.visitCallExpr(this)
    }

}

internal class LiteralExpr(val value: BigDecimal) : Expr() {

    override fun <R> accept(visitor: ExprVisitor<R>): R {
        return visitor.visitLiteralExpr(this)
    }

}

internal class VariableExpr(val name: Token) : Expr() {

    override fun <R> accept(visitor: ExprVisitor<R>): R {
        return visitor.visitVariableExpr(this)
    }

}

internal class GroupingExpr(val expression: Expr) : Expr() {

    override fun <R> accept(visitor: ExprVisitor<R>): R {
        return visitor.visitGroupingExpr(this)
    }

}

internal interface ExprVisitor<out R> {

    fun visitAssignExpr(expr: AssignExpr): R

    fun visitLogicalExpr(expr: LogicalExpr): R

    fun visitBinaryExpr(expr: BinaryExpr): R

    fun visitUnaryExpr(expr: UnaryExpr): R

    fun visitCallExpr(expr: CallExpr): R

    fun visitLiteralExpr(expr: LiteralExpr): R

    fun visitVariableExpr(expr: VariableExpr): R

    fun visitGroupingExpr(expr: GroupingExpr): R

}


abstract class Function {

    abstract fun call(arguments: List<BigDecimal>): BigDecimal

}



internal class Parser(private val tokens: List<Token>) {

    private var current = 0

    fun parse(): Expr {
        val expr = expression()

        if (!isAtEnd()) {
            throw ExpressionException("Expected end of expression, found '${peek().lexeme}'")
        }

        return expr
    }

    private fun expression(): Expr {
        return assignment()
    }

    private fun assignment(): Expr {
        val expr = or()

        if (match(TokenType.ASSIGN)) {
            val value = assignment()

            if (expr is VariableExpr) {
                val name = expr.name

                return AssignExpr(name, value)
            } else {
                throw ExpressionException("Invalid assignment target")
            }
        }

        return expr
    }

    private fun or(): Expr {
        var expr = and()

        while (match(TokenType.BAR_BAR)) {
            val operator = previous()
            val right = and()

            expr = LogicalExpr(expr, operator, right)
        }

        return expr
    }

    private fun and(): Expr {
        var expr = equality()

        while (match(TokenType.AMP_AMP)) {
            val operator = previous()
            val right = equality()

            expr = LogicalExpr(expr, operator, right)
        }

        return expr
    }

    private fun equality(): Expr {
        var left = comparison()

        while (match(TokenType.EQUAL_EQUAL, TokenType.NOT_EQUAL)) {
            val operator = previous()
            val right = comparison()

            left = BinaryExpr(left, operator, right)
        }

        return left
    }

    private fun comparison(): Expr {
        var left = addition()

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val operator = previous()
            val right = addition()

            left = BinaryExpr(left, operator, right)
        }

        return left
    }

    private fun addition(): Expr {
        var left = multiplication()

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val operator = previous()
            val right = multiplication()

            left = BinaryExpr(left, operator, right)
        }

        return left
    }

    private fun multiplication(): Expr {
        var left = unary()

        while (match(TokenType.STAR, TokenType.SLASH, TokenType.MODULO)) {
            val operator = previous()
            val right = unary()

            left = BinaryExpr(left, operator, right)
        }

        return left
    }

    private fun unary(): Expr {
        if (match(TokenType.MINUS)) {
            val operator = previous()
            val right = unary()

            return UnaryExpr(operator, right)
        }

        return exponent()
    }

    private fun exponent(): Expr {
        var left = call()

        if (match(TokenType.EXPONENT)) {
            val operator = previous()
            val right = unary()

            left = BinaryExpr(left, operator, right)
        }

        return left
    }

    private fun call(): Expr {
        if (TokenType.IDENTIFIER.matchTwo(TokenType.LEFT_PAREN)) {
            val (name, _) = previousTwo()

            val arguments = mutableListOf<Expr>()

            if (!check(TokenType.RIGHT_PAREN)) {
                do {
                    arguments += expression()
                } while (match(TokenType.COMMA))
            }

            TokenType.RIGHT_PAREN.consume("Expected ')' after function arguments")

            return CallExpr(name.lexeme, arguments)
        }

        return primary()
    }

    private fun primary(): Expr {
        if (match(TokenType.NUMBER)) {
            return LiteralExpr(previous().literal as BigDecimal)
        }

        if (match(TokenType.IDENTIFIER)) {
            return VariableExpr(previous())
        }

        if (match(TokenType.LEFT_PAREN)) {
            val expr = expression()

            TokenType.RIGHT_PAREN.consume("Expected ')' after '${previous().lexeme}'.")

            return GroupingExpr(expr)
        }

        throw ExpressionException("Expected expression after '${previous().lexeme}'.")
    }

    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()

                return true
            }
        }

        return false
    }

    private fun TokenType.matchTwo(second: TokenType): Boolean {
        val start = current

        if (match(this) && match(second)) {
            return true
        }

        current = start
        return false
    }

    private fun check(tokenType: TokenType): Boolean {
        return if (isAtEnd()) {
            false
        } else {
            peek().type === tokenType
        }
    }

    private fun TokenType.consume(message: String): Token {
        if (check(this)) return advance()

        throw ExpressionException(message)
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++

        return previous()
    }

    private fun isAtEnd() = peek().type == TokenType.EOF

    private fun peek() = tokens[current]

    private fun previous() = tokens[current - 1]

    private fun previousTwo() = Pair(tokens[current - 2], tokens[current - 1])

}



private fun invalidToken(c: Char) {
    throw ExpressionException("Invalid token '$c'")
}

internal class Scanner(private val source: String,
                       private val mathContext: MathContext) {

    private val tokens: MutableList<Token> = mutableListOf()
    private var start = 0
    private var current = 0

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            scanToken()
        }

        tokens.add(Token(TokenType.EOF, "", null))
        return tokens
    }

    private fun isAtEnd(): Boolean {
        return current >= source.length
    }

    private fun scanToken() {
        start = current

        when (val c = advance()) {
            ' ',
            '\r',
            '\t' -> {
                // Ignore whitespace.
            }
            '+' -> addToken(TokenType.PLUS)
            '-' -> addToken(TokenType.MINUS)
            '*' -> addToken(TokenType.STAR)
            '/' -> addToken(TokenType.SLASH)
            '%' -> addToken(TokenType.MODULO)
            '^' -> addToken(TokenType.EXPONENT)
            '=' -> if (match('=')) addToken(TokenType.EQUAL_EQUAL) else addToken(TokenType.ASSIGN)
            '!' -> if (match('=')) addToken(TokenType.NOT_EQUAL) else invalidToken(c)
            '>' -> if (match('=')) addToken(TokenType.GREATER_EQUAL) else addToken(TokenType.GREATER)
            '<' -> if (match('=')) addToken(TokenType.LESS_EQUAL) else addToken(TokenType.LESS)
            '|' -> if (match('|')) addToken(TokenType.BAR_BAR) else invalidToken(c)
            '&' -> if (match('&')) addToken(TokenType.AMP_AMP) else invalidToken(c)
            ',' -> addToken(TokenType.COMMA)
            '(' -> addToken(TokenType.LEFT_PAREN)
            ')' -> addToken(TokenType.RIGHT_PAREN)
            else -> {
                when {
                    c.isDigit() -> number()
                    c.isAlpha() -> identifier()
                    else -> invalidToken(c)
                }
            }
        }
    }

    private fun isDigit(char: Char,
                        previousChar: Char = '\u0000',
                        nextChar: Char = '\u0000'): Boolean {
        return char.isDigit() || when (char) {
            '.'      -> true
            'e', 'E' -> previousChar.isDigit() && (nextChar.isDigit() || nextChar == '+' || nextChar == '-')
            '+', '-' -> (previousChar == 'e' || previousChar == 'E') && nextChar.isDigit()
            else     -> false
        }
    }

    private fun number() {
        while (peek().isDigit()) advance()

        if (isDigit(peek(), peekPrevious(), peekNext())) {
            advance()
            while (isDigit(peek(), peekPrevious(), peekNext())) advance()
        }

        val value = source
                .substring(start, current)
                .toBigDecimal(mathContext)

        addToken(TokenType.NUMBER, value)
    }

    private fun identifier() {
        while (peek().isAlphaNumeric()) advance()

        addToken(TokenType.IDENTIFIER)
    }

    private fun advance() = source[current++]

    private fun peek(): Char {
        return if (isAtEnd()) {
            '\u0000'
        } else {
            source[current]
        }
    }

    private fun peekPrevious(): Char = if (current > 0) source[current - 1] else '\u0000'

    private fun peekNext(): Char {
        return if (current + 1 >= source.length) {
            '\u0000'
        } else {
            source[current + 1]
        }
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        current++
        return true
    }

    private fun addToken(type: TokenType) = addToken(type, null)

    private fun addToken(type: TokenType, literal: Any?) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal))
    }

    private fun Char.isAlphaNumeric() = isAlpha() || isDigit()

    private fun Char.isAlpha() = this in 'a'..'z'
            || this in 'A'..'Z'
            || this == '_'

    private fun Char.isDigit() = this == '.' || this in '0'..'9'

}



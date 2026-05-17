package app.hida.vault.nativemodule

import kotlin.math.*

/**
 * Calculator evaluation engine — recursive descent parser.
 * Supports: +, -, *, /, (), ^, !, %, sin, cos, tan, asin, acos, atan, ln, log, √
 * Degree/Radian mode for trig functions.
 */
object CalcEngine {

    private sealed class Token {
        data class Num(val value: Double) : Token()
        data class Op(val value: Char) : Token()
        data object LParen : Token()
        data object RParen : Token()
        data class Func(val name: String) : Token()
        data object Pow : Token()
        data object Fact : Token()
        data object Pct : Token()
    }

    private val FUNCTIONS = listOf("asin", "acos", "atan", "sin", "cos", "tan", "ln", "log", "√")

    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0

        while (i < expr.length) {
            val c = expr[i]

            if (c == ' ') { i++; continue }

            // Number
            if (c.isDigit() || (c == '.' && i + 1 < expr.length && expr[i + 1].isDigit())) {
                val sb = StringBuilder()
                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                    sb.append(expr[i]); i++
                }
                tokens.add(Token.Num(sb.toString().toDoubleOrNull() ?: 0.0))
                continue
            }

            // Functions (check before single-char operators)
            var matched = false
            for (fn in FUNCTIONS) {
                if (expr.startsWith(fn, i)) {
                    tokens.add(Token.Func(fn)); i += fn.length; matched = true; break
                }
            }
            if (matched) continue

            when (c) {
                '+', '-', '*', '/' -> { tokens.add(Token.Op(c)); i++ }
                '(' -> { tokens.add(Token.LParen); i++ }
                ')' -> { tokens.add(Token.RParen); i++ }
                '^' -> { tokens.add(Token.Pow); i++ }
                '!' -> { tokens.add(Token.Fact); i++ }
                '%' -> { tokens.add(Token.Pct); i++ }
                else -> i++ // skip unknown
            }
        }

        return tokens
    }

    private class Parser(private val tokens: List<Token>, private val degMode: Boolean) {
        private var pos = 0

        fun parse(): Double {
            if (tokens.isEmpty()) return 0.0
            return expression()
        }

        private fun peek(): Token? = if (pos < tokens.size) tokens[pos] else null

        private fun consume(): Token = tokens[pos++]

        private fun peekOpValue(): Char? {
            val t = peek()
            return if (t is Token.Op) t.value else null
        }

        // expression := term (('+' | '-') term)*
        private fun expression(): Double {
            var result = term()
            while (peekOpValue() == '+' || peekOpValue() == '-') {
                val op = peekOpValue(); consume()
                val right = term()
                result = if (op == '+') result + right else result - right
            }
            return result
        }

        // term := power (('*' | '/') power)*
        private fun term(): Double {
            var result = power()
            while (peekOpValue() == '*' || peekOpValue() == '/') {
                val op = peekOpValue(); consume()
                val right = power()
                result = if (op == '*') result * right else if (right != 0.0) result / right else Double.NaN
            }
            return result
        }

        // power := unary ('^' power)?  (right-associative)
        private fun power(): Double {
            var base = unary()
            if (peek() is Token.Pow) {
                consume()
                val exp = power()
                base = base.pow(exp)
            }
            return base
        }

        // unary := '-' unary | postfix
        private fun unary(): Double {
            if (peekOpValue() == '-') {
                val prev = if (pos > 0) tokens[pos - 1] else null
                if (prev == null || prev is Token.Op || prev is Token.LParen || prev is Token.Pow) {
                    consume()
                    return -unary()
                }
            }
            return postfix()
        }

        // postfix := primary ('!' | '%')*
        private fun postfix(): Double {
            var result = primary()
            while (peek() is Token.Fact || peek() is Token.Pct) {
                val tok = consume()
                result = if (tok is Token.Fact) factorial(result) else result / 100.0
            }
            return result
        }

        // primary := NUMBER | '(' expression ')' | FUNC '(' expression ')' | FUNC primary
        private fun primary(): Double {
            val tok = peek() ?: return 0.0

            // Number
            if (tok is Token.Num) {
                consume(); return tok.value
            }

            // Grouped expression
            if (tok is Token.LParen) {
                consume()
                val result = expression()
                if (peek() is Token.RParen) consume()
                return result
            }

            // Function call
            if (tok is Token.Func) {
                consume()
                val arg: Double = if (peek() is Token.LParen) {
                    consume()
                    val r = expression()
                    if (peek() is Token.RParen) consume()
                    r
                } else {
                    primary()
                }
                return evalFunc(tok.name, arg)
            }

            // Fallback
            consume(); return 0.0
        }

        private fun evalFunc(name: String, arg: Double): Double {
            fun toRad(deg: Double) = deg * PI / 180.0
            fun toDeg(rad: Double) = rad * 180.0 / PI

            return when (name) {
                "sin" -> sin(if (degMode) toRad(arg) else arg)
                "cos" -> cos(if (degMode) toRad(arg) else arg)
                "tan" -> tan(if (degMode) toRad(arg) else arg)
                "asin" -> if (degMode) toDeg(asin(arg)) else asin(arg)
                "acos" -> if (degMode) toDeg(acos(arg)) else acos(arg)
                "atan" -> if (degMode) toDeg(atan(arg)) else atan(arg)
                "ln" -> ln(arg)
                "log" -> log10(arg)
                "√" -> sqrt(arg)
                else -> Double.NaN
            }
        }
    }

    private fun factorial(n: Double): Double {
        if (n < 0 || n != floor(n)) return Double.NaN
        if (n > 170) return Double.POSITIVE_INFINITY
        var result = 1.0
        for (i in 2..n.toInt()) result *= i
        return result
    }

    fun evalExpr(expr: String, degMode: Boolean = true): Double {
        return try {
            val tokens = tokenize(expr)
            Parser(tokens, degMode).parse()
        } catch (e: Exception) {
            Double.NaN
        }
    }

    fun formatResult(v: Double): String {
        if (v.isNaN()) return "Error"
        if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
        if (v == truncate(v) && abs(v) < 1e12) {
            return v.toLong().toString()
        }
        return "%.8f".format(v).trimEnd('0').trimEnd('.')
    }

    fun formatWithCommas(num: String): String {
        if (num.isEmpty() || num == "-" || num == "Error" || num == "Infinity" || num == "-Infinity") return num
        val parts = num.split(".")
        val intPart = parts[0].replace(",", "")
        if (intPart.isEmpty()) return num

        val isNeg = intPart.startsWith("-")
        val digits = if (isNeg) intPart.substring(1) else intPart
        val sb = StringBuilder()
        for ((idx, ch) in digits.reversed().withIndex()) {
            if (idx > 0 && idx % 3 == 0) sb.append(',')
            sb.append(ch)
        }
        val formatted = sb.reverse().toString()
        val result = if (isNeg) "-$formatted" else formatted
        return if (parts.size > 1) "$result.${parts[1]}" else result
    }

    fun formatExpression(expr: String): String {
        if (expr.isEmpty()) return "0"
        if (expr == "Error") return "Error"

        val result = StringBuilder()
        val numBuf = StringBuilder()

        for (c in expr) {
            if (c.isDigit() || c == '.') {
                numBuf.append(c)
            } else {
                if (numBuf.isNotEmpty()) {
                    result.append(formatWithCommas(numBuf.toString()))
                    numBuf.clear()
                }
                result.append(c)
            }
        }
        if (numBuf.isNotEmpty()) result.append(formatWithCommas(numBuf.toString()))
        return result.toString()
    }

    private fun truncate(v: Double): Double = if (v >= 0) floor(v) else ceil(v)
}

/*
 *     This file is part of Launcher, a fork of Lawnchair Launcher.
 *
 *     Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair.allapps.search

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.pow

/**
 * Evaluates simple arithmetic typed into the search: + - * / ^ %, parentheses and decimal
 * numbers with a point or a comma ("2,5*4" or "(3+4)^2").
 */
object Calculator {

    /**
     * Returns the result, or null if the query is no calculation (a plain number is none).
     */
    fun evaluate(query: String): Double? {
        val expression = query.trim()
                .replace('×', '*').replace('÷', '/').replace('−', '-')
                .replace(" ", "")
        if (expression.isEmpty() || expression.none { it in OPERATORS }) return null
        if (expression.any { !it.isDigit() && it !in ALLOWED }) return null
        return try {
            val parser = Parser(expression.replace(',', '.'))
            val result = parser.parseExpression()
            if (!parser.atEnd || result.isNaN() || result.isInfinite()) null else result
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /**
     * Formats the result without needless decimals ("12" instead of "12.0").
     */
    fun format(value: Double, decimalSeparator: Char = '.'): String {
        val rounded = BigDecimal(value).round(MathContext(12, RoundingMode.HALF_UP))
                .stripTrailingZeros()
        val text = if (rounded.scale() < 0) rounded.setScale(0).toPlainString()
                else rounded.toPlainString()
        return text.replace('.', decimalSeparator)
    }

    private const val OPERATORS = "+-*/^%"
    private const val ALLOWED = "+-*/^%().,"

    private class Parser(private val text: String) {
        private var pos = 0

        val atEnd get() = pos >= text.length

        private fun peek() = if (pos < text.length) text[pos] else '\u0000'

        // expression = term (('+' | '-') term)*
        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                value = when (peek()) {
                    '+' -> { pos++; value + parseTerm() }
                    '-' -> { pos++; value - parseTerm() }
                    else -> return value
                }
            }
        }

        // term = factor (('*' | '/') factor)*
        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                value = when (peek()) {
                    '*' -> { pos++; value * parseFactor() }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        require(divisor != 0.0)
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        // factor = unary ('^' factor)?
        private fun parseFactor(): Double {
            val base = parseUnary()
            if (peek() == '^') {
                pos++
                return base.pow(parseFactor())
            }
            return base
        }

        // unary = ('-' | '+') unary | primary '%'?
        private fun parseUnary(): Double {
            return when (peek()) {
                '-' -> { pos++; -parseUnary() }
                '+' -> { pos++; parseUnary() }
                else -> {
                    val value = parsePrimary()
                    if (peek() == '%') {
                        pos++
                        value / 100
                    } else value
                }
            }
        }

        private fun parsePrimary(): Double {
            if (peek() == '(') {
                pos++
                val value = parseExpression()
                require(peek() == ')')
                pos++
                return value
            }
            val start = pos
            while (peek().isDigit() || peek() == '.') pos++
            require(pos > start)
            return text.substring(start, pos).toDoubleOrNull()
                    ?: throw IllegalArgumentException()
        }
    }
}

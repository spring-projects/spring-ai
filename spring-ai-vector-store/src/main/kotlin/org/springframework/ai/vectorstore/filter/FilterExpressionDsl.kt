package org.springframework.ai.vectorstore.filter

/**
 * DSL (Domain Specific Language) class for building filter expressions.
 * This class allows for the creation of complex filter expressions using a fluent API.
 *
 * example:
 * ``` kotlin
 *		filterExpression {
 * 			"field1".eq("value1")
 * 			and {
 * 				"field2".ne("value2")
 * 				"field3".gt(3)
 * 				"field4".gte(4)
 * 				or {
 * 					"field5".lt(5)
 * 					"field6".lte(6)
 * 				}
 * 			}
 * 		}
 * ```
 * @author Ahoo Wang
 */
@Suppress("TooManyFunctions")
class FilterExpressionDsl {
	// List to store individual filter expressions
	private val expressions: MutableList<Filter.Operand> = mutableListOf()

	/**
	 * Adds a filter expression to the list of expressions.
	 *
	 * @param expression The filter expression to add.
	 */
	fun expression(expression: Filter.Operand) {
		expressions.add(expression)
	}

	/**
	 * Creates and adds a filter expression based on the provided type, key, and value.
	 *
	 * @param type The type of the filter expression (e.g., EQ, GT, etc.).
	 * @param key The key to filter on.
	 * @param value The value to compare against.
	 */
	private fun expression(type: Filter.ExpressionType, key: String, value: Any) {
		expression(Filter.Expression(type, Filter.Key(key), Filter.Value(value)))
	}

	/**
	 * Assembles a list of filter operands into a single filter expression of the specified type.
	 * Optionally, the resulting expression can be grouped.
	 *
	 * @param type The type of the filter expression (e.g., AND, OR).
	 * @param group If true, the resulting expression will be wrapped in a group.
	 * @return The assembled filter expression.
	 */
	@Suppress("ReturnCount")
	private fun List<Filter.Operand>.assembly(
		type: Filter.ExpressionType,
		group: Boolean = false,
	): Filter.Operand {
		if (this.size == 1) {
			return this[0]
		}
		var exp = Filter.Expression(type, this[0], this[1])
		for (i in 2..this.size - 1) {
			exp = Filter.Expression(type, exp, this[i])
		}
		if (!group) {
			return exp
		}

		return Filter.Group(exp)
	}

	/**
	 * Combines a list of filter operands using the AND operator.
	 *
	 * @param group If true, the resulting expression will be wrapped in a group.
	 * @return The combined filter expression.
	 */
	private fun List<Filter.Operand>.and(group: Boolean = false): Filter.Operand {
		return this.assembly(Filter.ExpressionType.AND, group)
	}

	/**
	 * Combines a list of filter operands using the OR operator.
	 *
	 * @param group If true, the resulting expression will be wrapped in a group.
	 * @return The combined filter expression.
	 */
	private fun List<Filter.Operand>.or(group: Boolean = false): Filter.Operand {
		return this.assembly(Filter.ExpressionType.OR, group)
	}

	/**
	 * Creates a new FilterExpressionDsl instance, applies the provided block to it,
	 * and combines the resulting expressions using the AND operator.
	 *
	 * @param group If true, the resulting expression will be wrapped in a group.
	 * @param block A lambda that defines the filter expressions to be combined.
	 */
	fun and(group: Boolean = false, block: FilterExpressionDsl.() -> Unit) {
		val dsl = FilterExpressionDsl()
		dsl.block()
		if (dsl.expressions.isEmpty()) {
			return
		}
		expression(dsl.expressions.and(group))
	}

	/**
	 * Creates a new FilterExpressionDsl instance, applies the provided block to it,
	 * and combines the resulting expressions using the OR operator.
	 *
	 * @param group If true, the resulting expression will be wrapped in a group.
	 * @param block A lambda that defines the filter expressions to be combined.
	 */
	fun or(group: Boolean = false, block: FilterExpressionDsl.() -> Unit) {
		val dsl = FilterExpressionDsl()
		dsl.block()
		if (dsl.expressions.isEmpty()) {
			return
		}
		expression(dsl.expressions.or(group))
	}

	/**
	 * Creates a new FilterExpressionDsl instance, applies the provided block to it,
	 * and negates the resulting expression using the NOT operator.
	 *
	 * @param block A lambda that defines the filter expression to be negated.
	 */
	fun not(block: FilterExpressionDsl.() -> Unit) {
		val dsl = FilterExpressionDsl()
		dsl.block()
		if (dsl.expressions.isEmpty()) {
			return
		}
		val nestedCondition = dsl.build()
		expression(Filter.Expression(Filter.ExpressionType.NOT, nestedCondition))
	}

	/**
	 * Creates an equality filter expression.
	 *
	 * @param value The value to compare against.
	 */
	infix fun String.eq(value: Any) {
		expression(Filter.ExpressionType.EQ, this, value)
	}

	/**
	 * Creates a non-equality filter expression.
	 *
	 * @param value The value to compare against.
	 */
	infix fun String.ne(value: Any) {
		expression(Filter.ExpressionType.NE, this, value)
	}

	/**
	 * Creates a greater-than filter expression.
	 *
	 * @param value The value to compare against.
	 */
	infix fun String.gt(value: Any) {
		expression(Filter.ExpressionType.GT, this, value)
	}

	/**
	 * Creates a greater-than-or-equal-to filter expression.
	 *
	 * @param value The value to compare against.
	 */
	infix fun String.gte(value: Any) {
		expression(Filter.ExpressionType.GTE, this, value)
	}

	/**
	 * Creates a less-than filter expression.
	 *
	 * @param value The value to compare against.
	 */
	infix fun String.lt(value: Any) {
		expression(Filter.ExpressionType.LT, this, value)
	}

	/**
	 * Creates a less-than-or-equal-to filter expression.
	 *
	 * @param value The value to compare against.
	 */
	infix fun String.lte(value: Any) {
		expression(Filter.ExpressionType.LTE, this, value)
	}

	/**
	 * Creates an "in" filter expression, checking if the key is in the provided list of values.
	 *
	 * @param key The key to filter on.
	 * @param values The list of values to check against.
	 */
	fun isIn(key: String, vararg values: Any) {
		expression(Filter.ExpressionType.IN, key, values.toList())
	}

	/**
	 * Creates an "in" filter expression, checking if the key is in the provided list of values.
	 *
	 * @param values The list of values to check against.
	 */
	infix fun String.isIn(values: List<Any>) {
		expression(Filter.ExpressionType.IN, this, values)
	}

	/**
	 * Creates a "not in" filter expression, checking if the key is not in the provided list of values.
	 *
	 * @param key The key to filter on.
	 * @param values The list of values to check against.
	 */
	fun nin(key: String, vararg values: Any) {
		expression(Filter.ExpressionType.NIN, key, values.toList())
	}

	/**
	 * Creates a "not in" filter expression, checking if the key is not in the provided list of values.
	 *
	 * @param values The list of values to check against.
	 */
	infix fun String.nin(values: List<Any>) {
		expression(Filter.ExpressionType.NIN, this, values)
	}

	/**
	 * Converts a filter operand into a filter expression.
	 *
	 * @return The filter expression.
	 * @throws IllegalArgumentException if the operand type is unsupported.
	 */
	private fun Filter.Operand.asExpression(): Filter.Expression {
		return when (this) {
			is Filter.Expression -> this
			is Filter.Group -> this.content
			else -> throw IllegalArgumentException("Unsupported operand type: ${this::class.java.name}")
		}
	}

	/**
	 * Builds and returns the final filter expression.
	 *
	 * @return The final filter expression, or null if no expressions were added.
	 */
	@Suppress("ReturnCount")
	fun build(): Filter.Expression? {
		if (expressions.isEmpty()) {
			return null
		}
		if (expressions.size == 1) {
			return expressions[0].asExpression()
		}
		return expressions.and().asExpression()
	}

}

/**
 * Generates a filter expression based on the provided DSL block.
 *
 * This function takes a lambda expression as a parameter, which is executed in the context of `FilterExpressionDsl`,
 * allowing users to define filter conditions in a DSL manner.
 * Finally, the function returns a `Filter.Expression` object
 * representing the constructed filter expression.
 *
 * @param block A lambda expression executed in the context of `FilterExpressionDsl`, used to define filter conditions.
 * @return Returns a `Filter.Expression` object representing the filter expression constructed from the DSL.
 * If no conditions are defined in the DSL, it returns `null`.
 */
fun filterExpression(block: FilterExpressionDsl.() -> Unit): Filter.Expression? {
	val dsl = FilterExpressionDsl()
	dsl.block()
	return dsl.build()
}

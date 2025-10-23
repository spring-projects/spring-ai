package org.springframework.ai.vectorstore.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FilterExpressionDslTest {
	private val builder: FilterExpressionBuilder = FilterExpressionBuilder()

	@Test
	fun complexDsl() {
		val filterExpression = filterExpression {
			"field1".eq("value1")
			and {
				"field2".ne("value2")
				"field3".gt(3)
				"field4".gte(4)
				or {
					"field5".lt(5)
					"field6".lte(6)
				}
			}
		}

		assertThat(filterExpression).isEqualTo(
			builder.and(
				builder.eq("field1", "value1"),
				builder.and(
					builder.and(
						builder.and(
							builder.ne("field2", "value2"),
							builder.gt("field3", 3),
						),
						builder.gte("field4", 4)
					),
					builder.or(
						builder.lt("field5", 5),
						builder.lte("field6", 6)
					)
				)
			).build()
		)
	}

	@Test
	fun defaultAnd() {
		val filterExpression = filterExpression {
			"foo".eq("bar")
			"baz".eq("qux")
			"bar".eq("baz")
		}

		assertThat(filterExpression).isEqualTo(
			builder.and(
				builder.and(
					builder.eq("foo", "bar"),
					builder.eq("baz", "qux")
				),
				builder.eq("bar", "baz")
			).build()
		)
	}

	@Test
	fun groupAnd() {
		val filterExpression = filterExpression {
			and(group = true) {
				"foo".eq("bar")
				"baz".eq("qux")
			}
			"bar".eq("baz")
		}
		assertThat(filterExpression).isEqualTo(
			builder.and(
				builder.group(
					builder.and(
						builder.eq("foo", "bar"),
						builder.eq("baz", "qux")
					)
				),
				builder.eq("bar", "baz")
			).build()
		)
	}

	@Test
	fun groupOr() {
		val filterExpression = filterExpression {
			or(group = true) {
				"foo".eq("bar")
				"baz".eq("qux")
			}
			"bar".eq("baz")
		}
		assertThat(filterExpression).isEqualTo(
			builder.and(
				builder.group(
					builder.or(
						builder.eq("foo", "bar"),
						builder.eq("baz", "qux")
					)
				),
				builder.eq("bar", "baz")
			).build()
		)
	}

	@Test
	fun emptyFilterExpression() {
		val filterExpression = filterExpression {
		}
		assertThat(filterExpression).isNull()
	}

	@Test
	fun and() {
		val filterExpression = filterExpression {
			and {
				"foo".eq("bar")
				"baz".eq("qux")
			}
		}

		assertThat(filterExpression).isEqualTo(
			builder.and(
				builder.eq("foo", "bar"),
				builder.eq("baz", "qux")
			).build()
		)
	}

	@Test
	fun or() {
		val filterExpression = filterExpression {
			or {
				"foo".eq("bar")
				"baz".eq("qux")
			}
		}
		assertThat(filterExpression).isEqualTo(
			builder.or(
				builder.eq("foo", "bar"),
				builder.eq("baz", "qux")
			).build()
		)
	}

	@Test
	fun not() {
		val filterExpression = filterExpression {
			not {
				"foo".eq("bar")
				"baz".eq("qux")
			}
		}
		assertThat(filterExpression).isEqualTo(
			builder.not(
				builder.and(
					builder.eq("foo", "bar"),
					builder.eq("baz", "qux")
				)
			).build()
		)
	}

	@Test
	fun eq() {
		val filterExpression = filterExpression {
			"foo".eq("bar")
		}
		assertThat(filterExpression).isEqualTo(
			builder.eq("foo", "bar").build()
		)
	}

	@Test
	fun ne() {
		val filterExpression = filterExpression {
			"foo".ne("bar")
		}
		assertThat(filterExpression).isEqualTo(
			builder.ne("foo", "bar").build()
		)
	}

	@Test
	fun gt() {
		val filterExpression = filterExpression {
			"foo".gt(1)
		}
		assertThat(filterExpression).isEqualTo(
			builder.gt("foo", 1).build()
		)
	}

	@Test
	fun gte() {
		val filterExpression = filterExpression {
			"foo".gte(1)
		}
		assertThat(filterExpression).isEqualTo(
			builder.gte("foo", 1).build()
		)
	}

	@Test
	fun lt() {
		val filterExpression = filterExpression {
			"foo".lt(1)
		}
		assertThat(filterExpression).isEqualTo(
			builder.lt("foo", 1).build()
		)
	}

	@Test
	fun lte() {
		val filterExpression = filterExpression {
			"foo".lte(1)
		}
		assertThat(filterExpression).isEqualTo(
			builder.lte("foo", 1).build()
		)
	}

	@Test
	fun isIn() {
		val filterExpression = filterExpression {
			"foo".isIn(listOf("bar", "baz"))
		}
		assertThat(filterExpression).isEqualTo(
			builder.`in`("foo", listOf("bar", "baz")).build()
		)
	}

	@Test
	fun isInVararg() {
		val filterExpression = filterExpression {
			isIn("foo", "bar", "baz")
		}
		assertThat(filterExpression).isEqualTo(
			builder.`in`("foo", listOf("bar", "baz")).build()
		)
	}

	@Test
	fun nin() {
		val filterExpression = filterExpression {
			"foo".nin(listOf("bar", "baz"))
		}
		assertThat(filterExpression).isEqualTo(
			builder.nin("foo", listOf("bar", "baz")).build()
		)
	}

	@Test
	fun ninVararg() {
		val filterExpression = filterExpression {
			nin("foo", "bar", "baz")
		}
		assertThat(filterExpression).isEqualTo(
			builder.nin("foo", listOf("bar", "baz")).build()
		)
	}

}

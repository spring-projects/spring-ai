/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore.filter;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.converter.PrintFilterExpressionConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NOT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Christian Tzolov
 * @author Sun Yuhan
 * @author Dimitar Proynov
 */
public class FilterExpressionTextParserTests {

	FilterExpressionTextParser parser = new FilterExpressionTextParser();

	@Test
	public void testStringEscaping() {
		Expression exp = this.parser.parse("stuff == \"he'd say \\\"hello\\\", I'd say 'hi'\"");
		assertThat(((Value) exp.right()).value()).isEqualTo("he'd say \"hello\", I'd say 'hi'");

		exp = this.parser.parse("stuff == 'he\\'d say \"hello\", I\\'d say \\'hi\\''");
		assertThat(((Value) exp.right()).value()).isEqualTo("he'd say \"hello\", I'd say 'hi'");

		exp = this.parser.parse("stuff == 'This is a single backslash: \\\\'");
		assertThat(((Value) exp.right()).value()).isEqualTo("This is a single backslash: \\");

	}

	@Test
	public void testEQ() {
		// country == "BG"
		Expression exp = this.parser.parse("country == 'BG'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("country"), new Value("BG")));

		assertThat(this.parser.getCache().get("WHERE " + "country == 'BG'")).isEqualTo(exp);
	}

	@Test
	public void testIntegerLiterals() {
		// Literals within the Integer range remain Integer
		Expression exp = this.parser.parse("timestamp == " + Integer.MAX_VALUE);
		assertThat(exp.right()).isEqualTo(new Value(Integer.MAX_VALUE));

		// Literals beyond the Integer range fall back to Long
		exp = this.parser.parse("timestamp == " + Long.MAX_VALUE);
		assertThat(exp.right()).isEqualTo(new Value(Long.MAX_VALUE));

		exp = this.parser.parse("timestamp == " + Long.MIN_VALUE);
		assertThat(exp.right()).isEqualTo(new Value(Long.MIN_VALUE));

		// The explicit 'L' suffix keeps producing Long values
		exp = this.parser.parse("timestamp == 9223372036854775807L");
		assertThat(exp.right()).isEqualTo(new Value(Long.MAX_VALUE));

		// Large literals are supported inside IN lists
		exp = this.parser.parse("id in [" + Long.MAX_VALUE + ", 1]");
		assertThat(exp.right()).isEqualTo(new Value(List.of(Long.MAX_VALUE, 1)));
	}

	@Test
	public void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		Expression exp = this.parser.parse("genre == 'drama' && year >= 2020");
		assertThat(exp).isEqualTo(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
				new Expression(GTE, new Key("year"), new Value(2020))));

		assertThat(this.parser.getCache().get("WHERE " + "genre == 'drama' && year >= 2020")).isEqualTo(exp);
	}

	@Test
	public void tesIn() {
		// genre in ["comedy", "documentary", "drama"]
		Expression exp = this.parser.parse("genre in ['comedy', 'documentary', 'drama']");
		assertThat(exp)
			.isEqualTo(new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));

		assertThat(this.parser.getCache().get("WHERE " + "genre in ['comedy', 'documentary', 'drama']")).isEqualTo(exp);
	}

	@Test
	public void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		Expression exp = this.parser.parse("year >= 2020 OR country == \"BG\" AND city != \"Sofia\"");
		assertThat(exp).isEqualTo(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
				new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
						new Expression(NE, new Key("city"), new Value("Sofia")))));

		assertThat(this.parser.getCache().get("WHERE " + "year >= 2020 OR country == \"BG\" AND city != \"Sofia\""))
			.isEqualTo(exp);
	}

	@Test
	public void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		Expression exp = this.parser.parse("(year >= 2020 OR country == \"BG\") AND city NIN [\"Sofia\", \"Plovdiv\"]");

		assertThat(exp).isEqualTo(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));

		assertThat(this.parser.getCache()
			.get("WHERE " + "(year >= 2020 OR country == \"BG\") AND city NIN [\"Sofia\", \"Plovdiv\"]"))
			.isEqualTo(exp);
	}

	@Test
	public void tesBoolean() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		Expression exp = this.parser.parse("isOpen == true AND year >= 2020 AND country IN [\"BG\", \"NL\", \"US\"]");

		assertThat(exp).isEqualTo(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));
		assertThat(this.parser.getCache()
			.get("WHERE " + "isOpen == true AND year >= 2020 AND country IN [\"BG\", \"NL\", \"US\"]")).isEqualTo(exp);
	}

	@Test
	public void tesNot() {
		// NOT(isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"])
		Expression exp = this.parser
			.parse("not(isOpen == true AND year >= 2020 AND country IN [\"BG\", \"NL\", \"US\"])");

		assertThat(exp).isEqualTo(new Expression(NOT,
				new Group(new Expression(AND,
						new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
								new Expression(GTE, new Key("year"), new Value(2020))),
						new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US"))))),
				null));

		assertThat(this.parser.getCache()
			.get("WHERE " + "not(isOpen == true AND year >= 2020 AND country IN [\"BG\", \"NL\", \"US\"])"))
			.isEqualTo(exp);
	}

	@Test
	public void tesNotNin() {
		// NOT(country NOT IN ["BG", "NL", "US"])
		Expression exp = this.parser.parse("not(country NOT IN [\"BG\", \"NL\", \"US\"])");

		assertThat(exp).isEqualTo(new Expression(NOT,
				new Group(new Expression(NIN, new Key("country"), new Value(List.of("BG", "NL", "US")))), null));
	}

	@Test
	public void tesNotNin2() {
		// NOT country NOT IN ["BG", "NL", "US"]
		Expression exp = this.parser.parse("NOT country NOT IN [\"BG\", \"NL\", \"US\"]");

		assertThat(exp).isEqualTo(new Expression(NOT,
				new Expression(NIN, new Key("country"), new Value(List.of("BG", "NL", "US"))), null));
	}

	@Test
	public void tesNestedNot() {
		// NOT(isOpen == true AND year >= 2020 AND NOT(country IN ["BG", "NL", "US"]))
		Expression exp = this.parser
			.parse("not(isOpen == true AND year >= 2020 AND NOT(country IN [\"BG\", \"NL\", \"US\"]))");

		assertThat(exp).isEqualTo(new Expression(NOT,
				new Group(new Expression(AND,
						new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
								new Expression(GTE, new Key("year"), new Value(2020))),
						new Expression(NOT,
								new Group(new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))),
								null))),
				null));

		assertThat(this.parser.getCache()
			.get("WHERE " + "not(isOpen == true AND year >= 2020 AND NOT(country IN [\"BG\", \"NL\", \"US\"]))"))
			.isEqualTo(exp);
	}

	@Test
	public void testNotBindsTighterThanAnd() {
		// (NOT country == 'BG') AND year >= 2020
		String expText = "NOT country == 'BG' AND year >= 2020";
		Expression exp = this.parser.parse(expText);

		assertThat(exp).isEqualTo(
				new Expression(AND, new Expression(NOT, new Expression(EQ, new Key("country"), new Value("BG")), null),
						new Expression(GTE, new Key("year"), new Value(2020))));

		assertThat(this.parser.getCache().get("WHERE " + expText)).isEqualTo(exp);
	}

	@Test
	public void testNotBindsTighterThanOr() {
		// (NOT country == 'BG') OR year >= 2020
		String expText = "NOT country == 'BG' OR year >= 2020";
		Expression exp = this.parser.parse(expText);

		assertThat(exp).isEqualTo(
				new Expression(OR, new Expression(NOT, new Expression(EQ, new Key("country"), new Value("BG")), null),
						new Expression(GTE, new Key("year"), new Value(2020))));
	}

	@Test
	public void testNotAppliesToWholeGroupWhenParenthesized() {
		// NOT(country == 'BG' AND year >= 2020)
		Expression exp = this.parser.parse("NOT (country == 'BG' AND year >= 2020)");

		assertThat(exp).isEqualTo(new Expression(NOT,
				new Group(new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
						new Expression(GTE, new Key("year"), new Value(2020)))),
				null));
	}

	@Test
	public void testDecimal() {
		// temperature >= -15.6 && temperature <= +20.13
		String expText = "temperature >= -15.6 && temperature <= +20.13";
		Expression exp = this.parser.parse(expText);

		assertThat(exp).isEqualTo(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
				new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(this.parser.getCache().get("WHERE " + expText)).isEqualTo(exp);
	}

	@Test
	public void testLong() {
		Expression exp2 = this.parser.parse("biz_id == 3L");
		Expression exp3 = this.parser.parse("biz_id == -5L");

		assertThat(exp2).isEqualTo(new Expression(EQ, new Key("biz_id"), new Value(3L)));
		assertThat(exp3).isEqualTo(new Expression(EQ, new Key("biz_id"), new Value(-5L)));
	}

	@Test
	public void testIdentifiers() {
		Expression exp = this.parser.parse("'country.1' == 'BG'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("country.1"), new Value("BG")));

		exp = this.parser.parse("'country_1_2_3' == 'BG'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("country_1_2_3"), new Value("BG")));

		exp = this.parser.parse("\"country 1 2 3\" == 'BG'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("country 1 2 3"), new Value("BG")));

		// case where there is an actual quote in the identifier (assuming this is what
		// the user really wants
		// may not be supported by all VS impl., but this is correct at the DSL -> java
		// level
		exp = this.parser.parse("\"country \\\"1 2 3\" == 'BG'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("country \"1 2 3"), new Value("BG")));
	}

	@Test
	public void testUnescapedIdentifierWithUnderscores() {
		Expression exp = this.parser.parse("file_name == 'medicaid-wa-faqs.pdf'");
		assertThat(exp).isEqualTo(new Expression(EQ, new Key("file_name"), new Value("medicaid-wa-faqs.pdf")));
	}

	private static final int MAX_EXPRESSION_DEPTH = FilterExpressionTextParser.MAX_EXPRESSION_DEPTH;

	/** Tightest stack the depth budget is validated against. */
	private static final int SMALL_STACK_BYTES = 256 * 1024;

	private static String andChain(int joins) {
		StringBuilder expression = new StringBuilder("field_0 == 0");
		for (int i = 1; i <= joins; i++) {
			expression.append(" AND field_").append(i).append(" == ").append(i);
		}
		return expression.toString();
	}

	/**
	 * Runs {@code body} on a small-stack thread and rethrows what it threw, so an
	 * overflow fails the test instead of being absorbed by the roomy JUnit stack.
	 */
	private static void onSmallStackThread(Runnable body) throws InterruptedException {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread thread = new Thread(null, () -> {
			try {
				body.run();
			}
			catch (Throwable t) {
				failure.set(t);
			}
		}, "small-stack", SMALL_STACK_BYTES);
		thread.start();
		thread.join();
		Throwable t = failure.get();
		if (t instanceof RuntimeException runtimeException) {
			throw runtimeException;
		}
		if (t instanceof Error error) {
			throw error;
		}
		if (t != null) {
			throw new AssertionError(t);
		}
	}

	@Test
	public void deeplyNestedParenthesesAreRejectedInsteadOfCrashing() {
		String deeplyNested = "(".repeat(1000) + "country == 'BG'" + ")".repeat(1000);

		assertThatThrownBy(() -> this.parser.parse(deeplyNested))
			.isInstanceOf(FilterExpressionTextParser.FilterExpressionParseException.class)
			.hasMessageContaining("too complex");
	}

	@Test
	public void chainedNotIsRejectedInsteadOfCrashing() {
		String chainedNot = "NOT ".repeat(1000) + "country == 'BG'";

		assertThatThrownBy(() -> this.parser.parse(chainedNot))
			.isInstanceOf(FilterExpressionTextParser.FilterExpressionParseException.class)
			.hasMessageContaining("too complex");
	}

	@Test
	public void nestingWithinTheLimitStillParses() {
		String nested = "(".repeat(50) + "country == 'BG'" + ")".repeat(50);

		Expression exp = this.parser.parse(nested);

		assertThat(exp).isEqualTo(new Expression(EQ, new Key("country"), new Value("BG")));
	}

	/** A flat chain costs the parser no stack, but still one tree level per operator. */
	@Test
	public void longFlatAndChainIsRejectedOnceItExceedsTheDepthBudget() {
		assertThatThrownBy(() -> this.parser.parse(andChain(MAX_EXPRESSION_DEPTH + 1)))
			.isInstanceOf(FilterExpressionTextParser.FilterExpressionParseException.class)
			.hasMessageContaining("too complex");
	}

	@Test
	public void andChainAtTheDepthBudgetStillParses() {
		assertThatCode(() -> this.parser.parse(andChain(MAX_EXPRESSION_DEPTH))).doesNotThrowAnyException();
	}

	@Test
	public void orChainIsCountedTowardsTheDepthBudgetToo() {
		StringBuilder expression = new StringBuilder("field_0 == 0");
		for (int i = 1; i <= MAX_EXPRESSION_DEPTH + 1; i++) {
			expression.append(" OR field_").append(i).append(" == ").append(i);
		}

		assertThatThrownBy(() -> this.parser.parse(expression.toString()))
			.isInstanceOf(FilterExpressionTextParser.FilterExpressionParseException.class)
			.hasMessageContaining("too complex");
	}

	@Test
	public void nestingAndChainLengthShareTheSameBudget() {
		int half = MAX_EXPRESSION_DEPTH / 2 + 1;
		String halfAndHalf = "(".repeat(half) + andChain(half) + ")".repeat(half);

		assertThatThrownBy(() -> this.parser.parse(halfAndHalf))
			.isInstanceOf(FilterExpressionTextParser.FilterExpressionParseException.class)
			.hasMessageContaining("too complex");
	}

	/** The budget is on depth, not length: {@code constantArray} is not recursive. */
	@Test
	public void longFlatInListIsNotRejectedByLength() throws InterruptedException {
		StringBuilder expression = new StringBuilder("country IN [");
		for (int i = 0; i < 5000; i++) {
			if (i > 0) {
				expression.append(",");
			}
			expression.append("'value_").append(i).append("'");
		}
		expression.append("]");
		assertThat(expression.length()).isGreaterThan(4096);

		onSmallStackThread(() -> {
			Expression exp = this.parser.parse(expression.toString());
			new PrintFilterExpressionConverter().convertExpression(exp);
			exp.toString();
		});
	}

	@Test
	public void manyFlatNotInConditionsAreNotMiscountedAsNesting() {
		StringBuilder expression = new StringBuilder("country NOT IN ['BG']");
		for (int i = 0; i < 50; i++) {
			expression.append(" AND field_").append(i).append(" NOT IN ['x']");
		}

		assertThatCode(() -> this.parser.parse(expression.toString())).doesNotThrowAnyException();
	}

	@Test
	public void manyFlatIsNotNullConditionsAreNotMiscountedAsNesting() {
		StringBuilder expression = new StringBuilder("country IS NOT NULL");
		for (int i = 0; i < 50; i++) {
			expression.append(" AND field_").append(i).append(" IS NOT NULL");
		}

		assertThatCode(() -> this.parser.parse(expression.toString())).doesNotThrowAnyException();
	}

	/**
	 * The regression this fix is about: the consumers of the returned tree run outside
	 * the {@code StackOverflowError} guard in {@code parse()}, so the deepest expression
	 * the parser accepts has to be safe for them.
	 */
	@Test
	public void deepestAcceptedExpressionIsSafeForRecursiveConsumers() throws InterruptedException {
		// Worst shape the budget admits: prefix NOTs (whose conversion recurses through
		// FilterHelper.negate) filling the nesting allowance, plus a chain for the rest.
		int nots = MAX_EXPRESSION_DEPTH / 2;
		int joins = MAX_EXPRESSION_DEPTH - nots - 1;
		String worstCase = "NOT ".repeat(nots) + "(" + andChain(joins) + ")";

		onSmallStackThread(() -> {
			Expression exp = this.parser.parse(worstCase);
			new PrintFilterExpressionConverter().convertExpression(exp);
			exp.hashCode();
			exp.toString();
			assertThat(exp.equals(exp)).isTrue();
		});
	}

	@Test
	public void deepestAcceptedNestedGroupExpressionIsSafeForRecursiveConsumers() throws InterruptedException {
		int groups = MAX_EXPRESSION_DEPTH / 2;
		String worstCase = "(".repeat(groups) + andChain(MAX_EXPRESSION_DEPTH - groups) + ")".repeat(groups);

		onSmallStackThread(() -> {
			Expression exp = this.parser.parse(worstCase);
			new PrintFilterExpressionConverter().convertExpression(exp);
			exp.hashCode();
			exp.toString();
		});
	}

}

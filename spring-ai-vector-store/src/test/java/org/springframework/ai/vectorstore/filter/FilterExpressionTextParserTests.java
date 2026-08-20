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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
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

	/**
	 * Regression test for #6807: {@code FilterExpressionTextParser} instances must not
	 * share mutable error state. Each instance owns its own
	 * {@link DescriptiveErrorListener}, so a parse failure on one instance must not leak
	 * its error messages into another instance.
	 */
	@Test
	public void testErrorStateIsNotSharedAcrossInstances() {
		FilterExpressionTextParser parserA = new FilterExpressionTextParser();
		FilterExpressionTextParser parserB = new FilterExpressionTextParser();

		// parserB hits a syntax error; the exception must carry a non-empty message.
		try {
			parserB.parse("country =="); // missing right-hand side -> syntax error
			fail("Expected FilterExpressionParseException");
		}
		catch (FilterExpressionTextParser.FilterExpressionParseException expected) {
			assertThat(expected.getMessage()).isNotEmpty();
		}

		// A fresh failed parse on parserA records its own (non-empty) error, independent
		// of B's. With per-invocation listeners the two parses never share state.
		try {
			parserA.parse("city ==");
			fail("Expected FilterExpressionParseException");
		}
		catch (FilterExpressionTextParser.FilterExpressionParseException expected) {
			assertThat(expected.getMessage()).isNotEmpty();
		}
	}

	@Test
	public void testParallelParsingDoesNotMixErrorState() throws Exception {
		// Concurrent parses across distinct instances must not corrupt each other's error
		// state (the listener used to be a shared singleton with a mutable message list).
		int threads = 8;
		var exceptions = new ConcurrentLinkedQueue<String>();
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			var futures = new ArrayList<Future<?>>();
			for (int i = 0; i < threads; i++) {
				final int idx = i;
				futures.add(executor.submit(() -> {
					var p = new FilterExpressionTextParser();
					if (idx % 2 == 0) {
						p.parse("k" + idx + " == 'v'");
						// A successful parse must not throw.
					}
					else {
						try {
							p.parse("k" + idx + " ==");
							exceptions.add("thread " + idx + " should have failed");
						}
						catch (FilterExpressionTextParser.FilterExpressionParseException e) {
							// Each call owns its own error state; the message must be
							// present.
							if (e.getMessage() == null || e.getMessage().isBlank()) {
								exceptions.add("thread " + idx + " got an empty error message");
							}
						}
					}
				}));
			}
			for (var f : futures) {
				f.get(10, TimeUnit.SECONDS);
			}
		}
		finally {
			executor.shutdown();
		}
		assertThat(exceptions).isEmpty();
	}

	/**
	 * Regression test for #6807 (follow-up): per-invocation error state must also isolate
	 * concurrent {@code parse()} calls on the *same* parser instance. Even though the
	 * listener is now created inside {@code parse()}, two threads sharing one instance
	 * must not see each other's error messages.
	 */
	@Test
	public void testConcurrentParseOnSameInstanceDoesNotMixErrorState() throws Exception {
		var parser = new FilterExpressionTextParser();
		int threads = 8;
		var exceptions = new ConcurrentLinkedQueue<String>();
		// Count how many of the *expected-to-fail* threads (odd idx) actually threw,
		// so a regression that silently drops errors under concurrency is caught
		// (not just "message was empty").
		var failedCount = new AtomicInteger(0);
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			var futures = new ArrayList<Future<?>>();
			for (int i = 0; i < threads; i++) {
				final int idx = i;
				futures.add(executor.submit(() -> {
					String expression = (idx % 2 == 0) ? "k" + idx + " == 'v'" : "k" + idx + " ==";
					try {
						parser.parse(expression);
						if (idx % 2 != 0) {
							exceptions.add("thread " + idx + " should have failed");
						}
					}
					catch (FilterExpressionTextParser.FilterExpressionParseException e) {
						// Only the failing threads (odd idx) must have recorded an error.
						if (idx % 2 == 0) {
							exceptions.add("thread " + idx + " unexpectedly failed: " + e.getMessage());
						}
						else {
							failedCount.incrementAndGet();
							// The real regression guard: a concurrent parse() on the
							// shared
							// instance must not have cleared or lost this call's error
							// message.
							if (e.getMessage() == null || e.getMessage().isBlank()) {
								exceptions.add("thread " + idx + " got an empty error message under concurrency");
							}
						}
					}
				}));
			}
			for (var f : futures) {
				f.get(10, TimeUnit.SECONDS);
			}
		}
		finally {
			executor.shutdown();
		}
		// Exactly the 4 odd-indexed threads must have failed (no error lost/merged).
		assertThat(failedCount.get()).isEqualTo(4);
		assertThat(exceptions).isEmpty();
	}

}

/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vectorstore.redis;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Julien Ruaux
 * @author Brian Sam-Bodden
 */
class RedisFilterExpressionConverterTests {

	private static RedisFilterExpressionConverter converter(MetadataField... fields) {
		return new RedisFilterExpressionConverter(Arrays.asList(fields));
	}

	@Test
	void testEQ() {
		// country == "BG"
		String vectorExpr = converter(RedisVectorStore.MetadataField.tag("country"))
			.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("@country:{BG}");
	}

	@Test
	void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		String vectorExpr = converter(RedisVectorStore.MetadataField.tag("genre"),
				RedisVectorStore.MetadataField.numeric("year"))
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr).isEqualTo("@genre:{drama} @year:[2020 inf]");
	}

	@Test
	void tesIn() {
		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = converter(RedisVectorStore.MetadataField.tag("genre")).convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("@genre:{comedy | documentary | drama}");
	}

	@Test
	void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String vectorExpr = converter(RedisVectorStore.MetadataField.numeric("year"),
				RedisVectorStore.MetadataField.tag("country"), RedisVectorStore.MetadataField.tag("city"))
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Group(new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia"))))));
		assertThat(vectorExpr).isEqualTo("@year:[2020 inf] | (@country:{BG} -@city:{Sofia})");
	}

	@Test
	void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		String vectorExpr = converter(RedisVectorStore.MetadataField.numeric("year"),
				RedisVectorStore.MetadataField.tag("country"), RedisVectorStore.MetadataField.tag("city"))
			.convertExpression(new Expression(AND,
					new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
							new Expression(EQ, new Key("country"), new Value("BG")))),
					new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr).isEqualTo("(@year:[2020 inf] | @country:{BG}) -@city:{Sofia | Plovdiv}");
	}

	@Test
	void tesBoolean() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = converter(RedisVectorStore.MetadataField.numeric("year"),
				RedisVectorStore.MetadataField.tag("country"), RedisVectorStore.MetadataField.tag("isOpen"))
			.convertExpression(new Expression(AND,
					new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
							new Expression(GTE, new Key("year"), new Value(2020))),
					new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo("@isOpen:{true} @year:[2020 inf] @country:{BG | NL | US}");
	}

	@Test
	void testDecimal() {
		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = converter(RedisVectorStore.MetadataField.numeric("temperature"))
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(vectorExpr).isEqualTo("@temperature:[-15.6 inf] @temperature:[-inf 20.13]");
	}

	@Test
	void testComplexIdentifiers() {
		String vectorExpr = converter(RedisVectorStore.MetadataField.tag("country 1 2 3"))
			.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("@\"country 1 2 3\":{BG}");

		vectorExpr = converter(RedisVectorStore.MetadataField.tag("country 1 2 3"))
			.convertExpression(new Expression(EQ, new Key("'country 1 2 3'"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("@'country 1 2 3':{BG}");
	}

	@Test
	void testSpecialCharactersInValues() {
		// Test values with Redis special characters that need escaping
		String vectorExpr = converter(RedisVectorStore.MetadataField.tag("description"))
			.convertExpression(new Expression(EQ, new Key("description"), new Value("test@value{with}special|chars")));

		// Should properly escape special Redis characters
		assertThat(vectorExpr).isEqualTo("@description:{test@value{with}special|chars}");
	}

	@Test
	void testEmptyStringValues() {
		String vectorExpr = converter(RedisVectorStore.MetadataField.tag("status"))
			.convertExpression(new Expression(EQ, new Key("status"), new Value("")));

		assertThat(vectorExpr).isEqualTo("@status:{}");
	}

	@Test
	void testSingleItemInList() {
		String vectorExpr = converter(RedisVectorStore.MetadataField.tag("status"))
			.convertExpression(new Expression(IN, new Key("status"), new Value(List.of("active"))));

		assertThat(vectorExpr).isEqualTo("@status:{active}");
	}

	@Test
	void testWhitespaceInFieldNames() {
		String vectorExpr = converter(RedisVectorStore.MetadataField.tag("value with spaces"))
			.convertExpression(new Expression(EQ, new Key("\"value with spaces\""), new Value("test")));

		assertThat(vectorExpr).isEqualTo("@\"value with spaces\":{test}");
	}

	@Test
	void testNestedQuotedFieldNames() {
		String vectorExpr = converter(RedisVectorStore.MetadataField.tag("value \"with\" quotes"))
			.convertExpression(new Expression(EQ, new Key("\"value \\\"with\\\" quotes\""), new Value("test")));

		assertThat(vectorExpr).isEqualTo("@\"value \\\"with\\\" quotes\":{test}");
	}

}

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

package org.springframework.ai.vectorstore.neo4j;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Neo4jSearchFilterTests {

	@Test
	void bindsValuesSeparatelyFromEscapedIdentifiers() {
		String value = "value' OR true //";
		var converted = Neo4jSearchFilter.convert(
				new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("odd`key"), new Filter.Value(value)),
				List.of("odd`key"));
		assertThat(converted.predicate()).isEqualTo("node.`metadata.odd``key` = $filter0").doesNotContain(value);
		assertThat(converted.parameters()).containsEntry("filter0", value);
	}

	@ParameterizedTest
	@EnumSource(value = Filter.ExpressionType.class, names = { "OR", "NE", "IN", "NIN", "NOT", "ISNULL", "ISNOTNULL" })
	void rejectsUnsupportedOperators(Filter.ExpressionType type) {
		assertThatThrownBy(() -> Neo4jSearchFilter
			.convert(new Filter.Expression(type, new Filter.Key("field"), new Filter.Value("value")), List.of("field")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(type.name());
	}

	@ParameterizedTest
	@MethodSource("unsupportedValues")
	void rejectsUnsupportedValues(Object value) {
		assertThatThrownBy(() -> Neo4jSearchFilter.convert(
				new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("field"), new Filter.Value(value)),
				List.of("field")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("SEARCH filter");
	}

	static List<Object> unsupportedValues() {
		return List.of(List.of("value"), Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY);
	}

	@Test
	void rejectsMissingKeysAndMalformedOperands() {
		var expression = new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Key("field"),
				new Filter.Value("value"));
		assertThatThrownBy(() -> Neo4jSearchFilter.convert(expression, List.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("filterableMetadataFields");
		assertThatThrownBy(() -> Neo4jSearchFilter.convert(
				new Filter.Expression(Filter.ExpressionType.EQ, new Filter.Value("value"), new Filter.Key("field")),
				List.of("field")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("key on the left");
	}

	@Test
	void conversionStateIsIsolatedBetweenRequests() {
		var expression = new FilterExpressionTextParser().parse("field >= 1 && field < 5");
		var first = Neo4jSearchFilter.convert(expression, List.of("field"));
		var second = Neo4jSearchFilter.convert(expression, List.of("field"));
		assertThat(second).isEqualTo(first);
		assertThat(second.parameters()).containsEntry("filter0", 1).containsEntry("filter1", 5);
	}

}

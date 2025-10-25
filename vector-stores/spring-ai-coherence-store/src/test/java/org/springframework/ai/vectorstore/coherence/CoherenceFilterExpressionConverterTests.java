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

package org.springframework.ai.vectorstore.coherence;

import com.tangosol.util.Filters;
import com.tangosol.util.ValueExtractor;
import com.tangosol.util.extractor.ChainedExtractor;
import com.tangosol.util.extractor.UniversalExtractor;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
public class CoherenceFilterExpressionConverterTests {

	public static final CoherenceFilterExpressionConverter CONVERTER = new CoherenceFilterExpressionConverter();

	@Test
	void testEQ() {
		final Expression e = new FilterExpressionTextParser().parse("country == 'NL'");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.equal(extractor("country"), "NL"));
	}

	@Test
	void testNE() {
		final Expression e = new FilterExpressionTextParser().parse("country != 'NL'");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.notEqual(extractor("country"), "NL"));
	}

	@Test
	void testGT() {
		final Expression e = new FilterExpressionTextParser().parse("price > 100");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.greater(extractor("price"), 100));
	}

	@Test
	void testGTE() {
		final Expression e = new FilterExpressionTextParser().parse("price >= 100");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.greaterEqual(extractor("price"), 100));
	}

	@Test
	void testLT() {
		final Expression e = new FilterExpressionTextParser().parse("price < 100");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.less(extractor("price"), 100));
	}

	@Test
	void testLTE() {
		final Expression e = new FilterExpressionTextParser().parse("price <= 100");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.lessEqual(extractor("price"), 100));
	}

	@Test
	void testIN() {
		final Expression e = new FilterExpressionTextParser().parse("weather in [\"windy\", \"rainy\"]");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.in(extractor("weather"), "windy", "rainy"));
	}

	@Test
	void testNIN() {
		final Expression e = new FilterExpressionTextParser().parse("weather nin [\"windy\", \"rainy\"]");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.not(Filters.in(extractor("weather"), "windy", "rainy")));
	}

	@Test
	void testNOT() {
		final Expression e = new FilterExpressionTextParser().parse("NOT( weather in [\"windy\", \"rainy\"] )");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.not(Filters.in(extractor("weather"), "windy", "rainy")));
	}

	private ValueExtractor extractor(String property) {
		return new ChainedExtractor(new UniversalExtractor<>("metadata"), new UniversalExtractor<>(property));
	}

	@Test
	void testBooleanValues() {
		final Expression e1 = new FilterExpressionTextParser().parse("active == true");
		final Expression e2 = new FilterExpressionTextParser().parse("deleted == false");

		assertThat(CONVERTER.convert(e1)).isEqualTo(Filters.equal(extractor("active"), true));
		assertThat(CONVERTER.convert(e2)).isEqualTo(Filters.equal(extractor("deleted"), false));
	}

	@Test
	void testNumericValues() {
		final Expression intExpr = new FilterExpressionTextParser().parse("count == 42");
		final Expression doubleExpr = new FilterExpressionTextParser().parse("rating == 4.5");
		final Expression negativeExpr = new FilterExpressionTextParser().parse("temperature == -10");

		assertThat(CONVERTER.convert(intExpr)).isEqualTo(Filters.equal(extractor("count"), 42));
		assertThat(CONVERTER.convert(doubleExpr)).isEqualTo(Filters.equal(extractor("rating"), 4.5));
		assertThat(CONVERTER.convert(negativeExpr)).isEqualTo(Filters.equal(extractor("temperature"), -10));
	}

	@Test
	void testStringWithSpecialCharacters() {
		final Expression e = new FilterExpressionTextParser().parse("description == 'This has \"quotes\" and spaces'");
		assertThat(CONVERTER.convert(e))
			.isEqualTo(Filters.equal(extractor("description"), "This has \"quotes\" and spaces"));
	}

	@Test
	void testEmptyStringValue() {
		final Expression e = new FilterExpressionTextParser().parse("comment == ''");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.equal(extractor("comment"), ""));
	}

	@Test
	void testINWithMixedTypes() {
		final Expression e = new FilterExpressionTextParser().parse("status in [1, 'active', true]");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.in(extractor("status"), 1, "active", true));
	}

	@Test
	void testINWithSingleValue() {
		final Expression e = new FilterExpressionTextParser().parse("category in ['category1']");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.in(extractor("category"), "category1"));
	}

	@Test
	void testNINWithSingleValue() {
		final Expression e = new FilterExpressionTextParser().parse("category nin ['inactive']");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.not(Filters.in(extractor("category"), "inactive")));
	}

	@Test
	void testCategoryWithNumericComparison() {
		final Expression e = new FilterExpressionTextParser().parse("categoryId >= 5");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.greaterEqual(extractor("categoryId"), 5));
	}

}

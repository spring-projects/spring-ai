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

}

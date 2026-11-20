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

package org.springframework.ai.vectorstore.coherence;

import java.util.HashMap;
import java.util.Map;

import com.oracle.coherence.ai.DocumentChunk;
import com.oracle.coherence.ai.Float32Vector;
import com.tangosol.io.DefaultSerializer;
import com.tangosol.io.Serializer;
import com.tangosol.io.pof.ConfigurablePofContext;
import com.tangosol.util.ExternalizableHelper;
import com.tangosol.util.Filter;
import com.tangosol.util.Filters;
import com.tangosol.util.ValueExtractor;
import com.tangosol.util.extractor.ChainedExtractor;
import com.tangosol.util.filter.ExtractorFilter;
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
		return new ChainedExtractor(CoherenceFilterExpressionConverter.MetadataExtractor.INSTANCE,
				new CoherenceFilterExpressionConverter.MetadataKeyExtractor(property));
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

	@Test
	void testQuotedIdentifierWithoutParenthesesStillWorks() {
		final Expression e = new FilterExpressionTextParser().parse("\"field with spaces\" == 'value'");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.equal(extractor("field with spaces"), "value"));
	}

	@Test
	void testQuotedIdentifierWithParenthesesIsALiteralKey() {
		// Parentheses are not special: their use is never interpreted as a Coherence
		// method expression.
		final Expression e = new FilterExpressionTextParser().parse("\"size()\" == 2");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.equal(extractor("size()"), 2));
	}

	@Test
	void testQuotedIdentifierNamedClearIsALiteralKey() {
		final Expression e = new FilterExpressionTextParser().parse("\"clear()\" != 'sentinel'");
		assertThat(CONVERTER.convert(e)).isEqualTo(Filters.notEqual(extractor("clear()"), "sentinel"));
	}

	@Test
	void quotedIdentifierNamedSizeDoesNotInvokeMapSizeMethod() {
		final Expression e = new FilterExpressionTextParser().parse("\"size\" >= 0");
		final Filter<?> filter = CONVERTER.convert(e);

		Map<String, Object> metadata = new HashMap<>();
		metadata.put("country", "BG");
		metadata.put("city", "Sofia");

		assertThat(evaluate(filter, new DocumentChunk("hello", metadata, new Float32Vector(new float[] { 1.0f }))))
			.isFalse();
	}

	@Test
	void quotedIdentifierNamedEmptyDoesNotInvokeMapIsEmptyMethod() {
		final Expression e = new FilterExpressionTextParser().parse("\"empty\" == false");
		final Filter<?> filter = CONVERTER.convert(e);

		Map<String, Object> metadata = new HashMap<>();

		assertThat(evaluate(filter, new DocumentChunk("hello", metadata, new Float32Vector(new float[] { 1.0f }))))
			.isFalse();
	}

	@Test
	void quotedIdentifierNamedClearParenthesesDoesNotMutateMetadata() {
		final Expression e = new FilterExpressionTextParser().parse("\"clear()\" != 'sentinel'");
		final Filter<?> filter = CONVERTER.convert(e);

		Map<String, Object> metadata = new HashMap<>();
		metadata.put("country", "BG");
		metadata.put("city", "Sofia");

		// The metadata map has no literal "clear()" key, so the literal lookup
		// returns null, and null is legitimately not equal to "sentinel"
		assertThat(evaluate(filter, new DocumentChunk("hello", metadata, new Float32Vector(new float[] { 1.0f }))))
			.isTrue();
		assertThat(metadata).hasSize(2);
	}

	@Test
	void quotedIdentifierMatchesALiteralMetadataFieldActuallyNamedSize() {
		final Expression e = new FilterExpressionTextParser().parse("\"size\" == 3");
		final Filter<?> filter = CONVERTER.convert(e);

		Map<String, Object> metadata = new HashMap<>();
		metadata.put("size", 3);

		assertThat(evaluate(filter, new DocumentChunk("hello", metadata, new Float32Vector(new float[] { 1.0f }))))
			.isTrue();
	}

	@Test
	void convertedFilterUsesReflectionFreeExtractors() {
		final Expression e = new FilterExpressionTextParser().parse("country == 'BG'");
		final Filter<?> filter = CONVERTER.convert(e);

		// Coherence extractor equality is canonical-name based, so it cannot tell a
		// reflective extractor apart from a literal one with the same name. Assert the
		// concrete extractor types instead.
		final ValueExtractor[] extractors = ((ChainedExtractor) ((ExtractorFilter) filter).getValueExtractor())
			.getExtractors();
		assertThat(extractors).hasSize(2);
		assertThat(extractors[0]).isInstanceOf(CoherenceFilterExpressionConverter.MetadataExtractor.class);
		assertThat(extractors[1]).isInstanceOf(CoherenceFilterExpressionConverter.MetadataKeyExtractor.class);
	}

	@Test
	void convertedFilterSurvivesJavaSerialization() {
		assertRoundTrips(new DefaultSerializer());
	}

	@Test
	void convertedFilterSurvivesPofSerialization() {
		// A cluster configured with coherence.serializer=pof can only serialize
		// extractors registered as POF user types, which is what
		// spring-ai-coherence-pof-config.xml does through the PofConfigProvider SPI.
		assertRoundTrips(new ConfigurablePofContext("coherence-pof-config.xml"));
	}

	// Filters are shipped to the storage members that evaluate them, so every extractor
	// they hold must round-trip through whichever serializer the cluster is configured
	// with.
	private void assertRoundTrips(Serializer serializer) {
		final Expression e = new FilterExpressionTextParser().parse("country == 'BG'");
		final Filter<?> filter = CONVERTER.convert(e);

		final Object deserialized = ExternalizableHelper.fromBinary(ExternalizableHelper.toBinary(filter, serializer),
				serializer);

		assertThat(deserialized).isEqualTo(filter);
	}

	private boolean evaluate(Filter<?> filter, DocumentChunk chunk) {
		return ((Filter<DocumentChunk>) filter).evaluate(chunk);
	}

}

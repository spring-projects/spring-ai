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

package org.springframework.ai.vectorstore.typesense;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LT;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Pablo Sanchidrian
 */
class TypesenseFilterExpressionConverterTests {

	final FilterExpressionConverter converter = new TypesenseFilterExpressionConverter();

	@Test
	void testEQ() {
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata.country: \"BG\"");
	}

	@Test
	void testEqAndGte() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr).isEqualTo("metadata.genre: \"drama\" && metadata.year: >= 2020");
	}

	@Test
	void testIn() {
		String vectorExpr = this.converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("metadata.genre: [\"comedy\",\"documentary\",\"drama\"]");
	}

	@Test
	void testNe() {
		String vectorExpr = this.converter.convertExpression(new Expression(NE, new Key("city"), new Value("Sofia")));
		assertThat(vectorExpr).isEqualTo("metadata.city: != \"Sofia\"");
	}

	@Test
	void testNin() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv"))));
		assertThat(vectorExpr).isEqualTo("metadata.city: != [\"Sofia\",\"Plovdiv\"]");
	}

	@Test
	void testGt() {
		String vectorExpr = this.converter.convertExpression(new Expression(GT, new Key("year"), new Value(2020)));
		assertThat(vectorExpr).isEqualTo("metadata.year: > 2020");
	}

	@Test
	void testLt() {
		String vectorExpr = this.converter.convertExpression(new Expression(LT, new Key("year"), new Value(2020)));
		assertThat(vectorExpr).isEqualTo("metadata.year: < 2020");
	}

	@Test
	void testLte() {
		String vectorExpr = this.converter.convertExpression(new Expression(LTE, new Key("year"), new Value(2020)));
		assertThat(vectorExpr).isEqualTo("metadata.year: <= 2020");
	}

	@Test
	void testOr() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(OR, new Expression(EQ, new Key("country"), new Value("BG")),
					new Expression(EQ, new Key("country"), new Value("NL"))));
		assertThat(vectorExpr).isEqualTo("metadata.country: \"BG\" || metadata.country: \"NL\"");
	}

	@Test
	void testBoolean() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr).isEqualTo("metadata.isOpen: true && metadata.year: >= 2020");
	}

	@Test
	void testDecimal() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));
		assertThat(vectorExpr).isEqualTo("metadata.temperature: >= -15.6 && metadata.temperature: <= 20.13");
	}

	@Test
	void testNestedFieldWithDots() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("address.city"), new Value("Sofia")));
		assertThat(vectorExpr).isEqualTo("metadata.address.city: \"Sofia\"");
	}

	@Test
	void testHyphenInFieldName() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("my-field"), new Value("test")));
		assertThat(vectorExpr).isEqualTo("metadata.my-field: \"test\"");
	}

	@Test
	void testQuotedKeyStrippedAndValidated() {
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("'country'"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata.country: \"BG\"");

		vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("\"country\""), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata.country: \"BG\"");
	}

	// Security tests - key injection prevention via character validation

	@Test
	void keyWithColonIsRejected() {
		assertThatThrownBy(
				() -> this.converter.convertExpression(new Expression(EQ, new Key("field:evil"), new Value("v"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not allowed filter identifier name");
	}

	@Test
	void keyWithOperatorInjectionIsRejected() {
		assertThatThrownBy(() -> this.converter
			.convertExpression(new Expression(EQ, new Key("country:BG && metadata.secret"), new Value("v"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not allowed filter identifier name");
	}

	@Test
	void keyWithSpaceIsRejected() {
		assertThatThrownBy(
				() -> this.converter.convertExpression(new Expression(EQ, new Key("my field"), new Value("v"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not allowed filter identifier name");
	}

	@Test
	void keyWithBracketsIsRejected() {
		assertThatThrownBy(
				() -> this.converter.convertExpression(new Expression(EQ, new Key("field[0]"), new Value("v"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not allowed filter identifier name");
	}

	@Test
	void keyWithPipeIsRejected() {
		assertThatThrownBy(
				() -> this.converter.convertExpression(new Expression(EQ, new Key("field||evil"), new Value("v"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not allowed filter identifier name");
	}

	@Test
	void quotedKeyWithSpaceIsStillRejected() {
		// Outer quotes are stripped but the bare identifier with spaces is still
		// invalid for Typesense field names
		assertThatThrownBy(
				() -> this.converter.convertExpression(new Expression(EQ, new Key("\"my field\""), new Value("v"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Not allowed filter identifier name");
	}

}

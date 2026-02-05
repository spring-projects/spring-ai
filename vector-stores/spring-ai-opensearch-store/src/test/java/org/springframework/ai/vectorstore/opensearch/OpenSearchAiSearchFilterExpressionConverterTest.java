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

package org.springframework.ai.vectorstore.opensearch;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

class OpenSearchAiSearchFilterExpressionConverterTest {

	final FilterExpressionConverter converter = new OpenSearchAiSearchFilterExpressionConverter();

	@Test
	public void testDate() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(EQ, new Filter.Key("activationDate"),
				new Filter.Value(new Date(1704637752148L))));
		assertThat(vectorExpr).isEqualTo("metadata.activationDate:2024-01-07T14:29:12Z");

		vectorExpr = this.converter.convertExpression(
				new Filter.Expression(EQ, new Filter.Key("activationDate"), new Filter.Value("1970-01-01T00:00:02Z")));
		assertThat(vectorExpr).isEqualTo("metadata.activationDate:1970-01-01T00:00:02Z");
	}

	@Test
	public void testEQ() {
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata.country:BG");
	}

	@Test
	public void tesEqAndGte() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(EQ, new Filter.Key("genre"), new Filter.Value("drama")),
				new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020))));
		assertThat(vectorExpr).isEqualTo("metadata.genre:drama AND metadata.year:>=2020");
	}

	@Test
	public void tesIn() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(IN, new Filter.Key("genre"),
				new Filter.Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("metadata.genre:(comedy OR documentary OR drama)");
	}

	@Test
	public void testNe() {
		String vectorExpr = this.converter.convertExpression(
				new Filter.Expression(OR, new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)),
						new Filter.Expression(AND,
								new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")),
								new Filter.Expression(NE, new Filter.Key("city"), new Filter.Value("Sofia")))));
		assertThat(vectorExpr).isEqualTo("metadata.year:>=2020 OR metadata.country:BG AND metadata.city: NOT Sofia");
	}

	@Test
	public void testGroup() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Group(new Filter.Expression(OR,
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020)),
						new Filter.Expression(EQ, new Filter.Key("country"), new Filter.Value("BG")))),
				new Filter.Expression(NIN, new Filter.Key("city"), new Filter.Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr)
			.isEqualTo("(metadata.year:>=2020 OR metadata.country:BG) AND NOT metadata.city:(Sofia OR Plovdiv)");
	}

	@Test
	public void testBoolean() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(AND, new Filter.Expression(EQ, new Filter.Key("isOpen"), new Filter.Value(true)),
						new Filter.Expression(GTE, new Filter.Key("year"), new Filter.Value(2020))),
				new Filter.Expression(IN, new Filter.Key("country"), new Filter.Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr)
			.isEqualTo("metadata.isOpen:true AND metadata.year:>=2020 AND metadata.country:(BG OR NL OR US)");
	}

	@Test
	public void testDecimal() {
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Expression(GTE, new Filter.Key("temperature"), new Filter.Value(-15.6)),
				new Filter.Expression(LTE, new Filter.Key("temperature"), new Filter.Value(20.13))));

		assertThat(vectorExpr).isEqualTo("metadata.temperature:>=-15.6 AND metadata.temperature:<=20.13");
	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("\"country 1 2 3\""), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata.country 1 2 3:BG");

		vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("'country 1 2 3'"), new Filter.Value("BG")));
		assertThat(vectorExpr).isEqualTo("metadata.country 1 2 3:BG");
	}

	@Test
	public void testEmptyList() {
		// category IN []
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(IN, new Filter.Key("category"), new Filter.Value(List.of())));
		assertThat(vectorExpr).isEqualTo("metadata.category:()");
	}

	@Test
	public void testSingleItemList() {
		// status IN ["active"]
		String vectorExpr = this.converter.convertExpression(
				new Filter.Expression(IN, new Filter.Key("status"), new Filter.Value(List.of("active"))));
		assertThat(vectorExpr).isEqualTo("metadata.status:(active)");
	}

	@Test
	public void testNullValue() {
		// description == null
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("description"), new Filter.Value(null)));
		assertThat(vectorExpr).isEqualTo("metadata.description:null");
	}

	@Test
	public void testNestedJsonPath() {
		// entity.profile.name == "EntityA"
		String vectorExpr = this.converter.convertExpression(
				new Filter.Expression(EQ, new Filter.Key("entity.profile.name"), new Filter.Value("EntityA")));
		assertThat(vectorExpr).isEqualTo("metadata.entity.profile.name:EntityA");
	}

	@Test
	public void testNumericStringValue() {
		// id == "1"
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("id"), new Filter.Value("1")));
		assertThat(vectorExpr).isEqualTo("metadata.id:1");
	}

	@Test
	public void testZeroValue() {
		// count == 0
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("count"), new Filter.Value(0)));
		assertThat(vectorExpr).isEqualTo("metadata.count:0");
	}

	@Test
	public void testComplexNestedGroups() {
		// ((fieldA >= 100 AND fieldB == "X1") OR (fieldA >= 50 AND fieldB == "Y2")) AND
		// fieldC != "inactive"
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND,
				new Filter.Group(new Filter.Expression(OR,
						new Filter.Group(new Filter.Expression(AND,
								new Filter.Expression(GTE, new Filter.Key("fieldA"), new Filter.Value(100)),
								new Filter.Expression(EQ, new Filter.Key("fieldB"), new Filter.Value("X1")))),
						new Filter.Group(new Filter.Expression(AND,
								new Filter.Expression(GTE, new Filter.Key("fieldA"), new Filter.Value(50)),
								new Filter.Expression(EQ, new Filter.Key("fieldB"), new Filter.Value("Y2")))))),
				new Filter.Expression(NE, new Filter.Key("fieldC"), new Filter.Value("inactive"))));

		assertThat(vectorExpr).isEqualTo(
				"((metadata.fieldA:>=100 AND metadata.fieldB:X1) OR (metadata.fieldA:>=50 AND metadata.fieldB:Y2)) AND metadata.fieldC: NOT inactive");
	}

	@Test
	public void testMixedDataTypes() {
		// active == true AND score >= 1.5 AND tags IN ["featured", "premium"] AND version
		// == 1
		String vectorExpr = this.converter.convertExpression(new Filter.Expression(AND, new Filter.Expression(AND,
				new Filter.Expression(AND, new Filter.Expression(EQ, new Filter.Key("active"), new Filter.Value(true)),
						new Filter.Expression(GTE, new Filter.Key("score"), new Filter.Value(1.5))),
				new Filter.Expression(IN, new Filter.Key("tags"), new Filter.Value(List.of("featured", "premium")))),
				new Filter.Expression(EQ, new Filter.Key("version"), new Filter.Value(1))));

		assertThat(vectorExpr).isEqualTo(
				"metadata.active:true AND metadata.score:>=1.5 AND metadata.tags:(featured OR premium) AND metadata.version:1");
	}

	@Test
	public void testNinWithMixedTypes() {
		// status NIN ["A", "B", "C"]
		String vectorExpr = this.converter.convertExpression(
				new Filter.Expression(NIN, new Filter.Key("status"), new Filter.Value(List.of("A", "B", "C"))));
		assertThat(vectorExpr).isEqualTo("NOT metadata.status:(A OR B OR C)");
	}

	@Test
	public void testEmptyStringValue() {
		// description != ""
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(NE, new Filter.Key("description"), new Filter.Value("")));
		assertThat(vectorExpr).isEqualTo("metadata.description: NOT ");
	}

	@Test
	public void testArrayIndexAccess() {
		// tags[0] == "important"
		String vectorExpr = this.converter
			.convertExpression(new Filter.Expression(EQ, new Filter.Key("tags[0]"), new Filter.Value("important")));
		assertThat(vectorExpr).isEqualTo("metadata.tags[0]:important");
	}

}

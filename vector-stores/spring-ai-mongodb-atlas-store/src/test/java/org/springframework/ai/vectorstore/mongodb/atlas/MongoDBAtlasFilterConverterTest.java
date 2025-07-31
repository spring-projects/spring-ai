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

package org.springframework.ai.vectorstore.mongodb.atlas;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;

import static org.assertj.core.api.Assertions.assertThat;
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
 * @author Christopher Smith
 */
public class MongoDBAtlasFilterConverterTest {

	FilterExpressionConverter converter = new MongoDBAtlasFilterExpressionConverter();

	@Test
	public void testEQ() {
		// country == "BG"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("{\"metadata.country\":{$eq:\"BG\"}}");
	}

	@Test
	public void tesEqAndGte() {
		// genre == "drama" AND year >= 2020
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr)
			.isEqualTo("{$and:[{\"metadata.genre\":{$eq:\"drama\"}},{\"metadata.year\":{$gte:2020}}]}");
	}

	@Test
	public void tesIn() {
		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = this.converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("{\"metadata.genre\":{$in:[\"comedy\",\"documentary\",\"drama\"]}}");
	}

	@Test
	public void testNe() {
		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String vectorExpr = this.converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(vectorExpr).isEqualTo(
				"{$or:[{\"metadata.year\":{$gte:2020}},{$and:[{\"metadata.country\":{$eq:\"BG\"}},{\"metadata.city\":{$ne:\"Sofia\"}}]}]}");
	}

	@Test
	public void testGroup() {
		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));
		assertThat(vectorExpr).isEqualTo(
				"{$and:[{$or:[{\"metadata.year\":{$gte:2020}},{\"metadata.country\":{$eq:\"BG\"}}]},{\"metadata.city\":{$nin:[\"Sofia\",\"Plovdiv\"]}}]}");
	}

	@Test
	public void testBoolean() {
		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo(
				"{$and:[{$and:[{\"metadata.isOpen\":{$eq:true}},{\"metadata.year\":{$gte:2020}}]},{\"metadata.country\":{$in:[\"BG\",\"NL\",\"US\"]}}]}");
	}

	@Test
	public void testDecimal() {
		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(vectorExpr)
			.isEqualTo("{$and:[{\"metadata.temperature\":{$gte:-15.6}},{\"metadata.temperature\":{$lte:20.13}}]}");
	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("{\"metadata.country 1 2 3\":{$eq:\"BG\"}}");

		vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("'country 1 2 3'"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("{\"metadata.country 1 2 3\":{$eq:\"BG\"}}");
	}

	@Test
	public void testLt() {
		// value < 100
		String vectorExpr = this.converter.convertExpression(new Expression(LT, new Key("value"), new Value(100)));
		assertThat(vectorExpr).isEqualTo("{\"metadata.value\":{$lt:100}}");
	}

	@Test
	public void testLte() {
		// value <= 100
		String vectorExpr = this.converter.convertExpression(new Expression(LTE, new Key("value"), new Value(100)));
		assertThat(vectorExpr).isEqualTo("{\"metadata.value\":{$lte:100}}");
	}

	@Test
	public void testGt() {
		// value > 100
		String vectorExpr = this.converter.convertExpression(new Expression(GT, new Key("value"), new Value(100)));
		assertThat(vectorExpr).isEqualTo("{\"metadata.value\":{$gt:100}}");
	}

	@Test
	public void testNin() {
		// region not in ["A", "B", "C"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("region"), new Value(List.of("A", "B", "C"))));
		assertThat(vectorExpr).isEqualTo("{\"metadata.region\":{$nin:[\"A\",\"B\",\"C\"]}}");
	}

	@Test
	public void testComplexNestedGroups() {
		// ((value >= 100 AND type == "primary") OR (value <= 50 AND type == "secondary"))
		// AND region == "X"
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR,
						new Group(new Expression(AND, new Expression(GTE, new Key("value"), new Value(100)),
								new Expression(EQ, new Key("type"), new Value("primary")))),
						new Group(new Expression(AND, new Expression(LTE, new Key("value"), new Value(50)),
								new Expression(EQ, new Key("type"), new Value("secondary")))))),
				new Expression(EQ, new Key("region"), new Value("X"))));

		assertThat(vectorExpr).isEqualTo(
				"{$and:[{$or:[{$and:[{\"metadata.value\":{$gte:100}},{\"metadata.type\":{$eq:\"primary\"}}]},{$and:[{\"metadata.value\":{$lte:50}},{\"metadata.type\":{$eq:\"secondary\"}}]}]},{\"metadata.region\":{$eq:\"X\"}}]}");
	}

	@Test
	public void testNullValue() {
		// status == null
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("status"), new Value(null)));
		assertThat(vectorExpr).isEqualTo("{\"metadata.status\":{$eq:null}}");
	}

	@Test
	public void testEmptyString() {
		// name == ""
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("name"), new Value("")));
		assertThat(vectorExpr).isEqualTo("{\"metadata.name\":{$eq:\"\"}}");
	}

	@Test
	public void testNumericString() {
		// id == "12345"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("id"), new Value("12345")));
		assertThat(vectorExpr).isEqualTo("{\"metadata.id\":{$eq:\"12345\"}}");
	}

	@Test
	public void testLongValue() {
		// timestamp >= 1640995200000L
		String vectorExpr = this.converter
			.convertExpression(new Expression(GTE, new Key("timestamp"), new Value(1640995200000L)));
		assertThat(vectorExpr).isEqualTo("{\"metadata.timestamp\":{$gte:1640995200000}}");
	}

	@Test
	public void testFloatValue() {
		// score >= 4.5f
		String vectorExpr = this.converter.convertExpression(new Expression(GTE, new Key("score"), new Value(4.5f)));
		assertThat(vectorExpr).isEqualTo("{\"metadata.score\":{$gte:4.5}}");
	}

	@Test
	public void testMixedTypesList() {
		// tags in [1, "priority", true]
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("tags"), new Value(List.of(1, "priority", true))));
		assertThat(vectorExpr).isEqualTo("{\"metadata.tags\":{$in:[1,\"priority\",true]}}");
	}

	@Test
	public void testEmptyList() {
		// categories in []
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("categories"), new Value(List.of())));
		assertThat(vectorExpr).isEqualTo("{\"metadata.categories\":{$in:[]}}");
	}

	@Test
	public void testSingleItemList() {
		// status in ["active"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("status"), new Value(List.of("active"))));
		assertThat(vectorExpr).isEqualTo("{\"metadata.status\":{$in:[\"active\"]}}");
	}

	@Test
	public void testKeyWithDots() {
		// "value.field" >= 18
		String vectorExpr = this.converter
			.convertExpression(new Expression(GTE, new Key("value.field"), new Value(18)));
		assertThat(vectorExpr).isEqualTo("{\"metadata.value.field\":{$gte:18}}");
	}

	@Test
	public void testKeyWithSpecialCharacters() {
		// "field-name_with@symbols" == "value"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("field-name_with@symbols"), new Value("value")));
		assertThat(vectorExpr).isEqualTo("{\"metadata.field-name_with@symbols\":{$eq:\"value\"}}");
	}

	@Test
	public void testTripleAnd() {
		// value >= 100 AND type == "primary" AND region == "X"
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(GTE, new Key("value"), new Value(100)),
						new Expression(EQ, new Key("type"), new Value("primary"))),
				new Expression(EQ, new Key("region"), new Value("X"))));

		assertThat(vectorExpr).isEqualTo(
				"{$and:[{$and:[{\"metadata.value\":{$gte:100}},{\"metadata.type\":{$eq:\"primary\"}}]},{\"metadata.region\":{$eq:\"X\"}}]}");
	}

	@Test
	public void testTripleOr() {
		// value < 50 OR value > 200 OR type == "special"
		String vectorExpr = this.converter.convertExpression(new Expression(OR,
				new Expression(OR, new Expression(LT, new Key("value"), new Value(50)),
						new Expression(GT, new Key("value"), new Value(200))),
				new Expression(EQ, new Key("type"), new Value("special"))));

		assertThat(vectorExpr).isEqualTo(
				"{$or:[{$or:[{\"metadata.value\":{$lt:50}},{\"metadata.value\":{$gt:200}}]},{\"metadata.type\":{$eq:\"special\"}}]}");
	}

	@Test
	public void testZeroValues() {
		// count == 0
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("count"), new Value(0)));
		assertThat(vectorExpr).isEqualTo("{\"metadata.count\":{$eq:0}}");
	}

	@Test
	public void testBooleanFalse() {
		// enabled == false
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("enabled"), new Value(false)));
		assertThat(vectorExpr).isEqualTo("{\"metadata.enabled\":{$eq:false}}");
	}

	@Test
	public void testVeryLongString() {
		// Test with a very long string value
		String longValue = "This is a very long string that might be used as a value in a filter expression to test how the converter handles lengthy text content that could potentially cause issues with string manipulation or JSON formatting";
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("content"), new Value(longValue)));
		assertThat(vectorExpr).isEqualTo("{\"metadata.content\":{$eq:\"" + longValue + "\"}}");
	}

}

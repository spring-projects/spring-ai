/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.vectorstore;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.converter.FilterExpressionConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NIN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Christian Tzolov
 */
public class WeaviateFilterExpressionConverterTests {

	private static String format(String text) {
		return text.trim().replace(" " + System.lineSeparator(), System.lineSeparator()) + "\n";
	}

	@Test
	public void testMissingFilterName() {

		FilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of());

		assertThatThrownBy(() -> {
			converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		}).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Not allowed filter identifier name: country. Consider adding it to WeaviateVectorStore#filterMetadataKeys.");
	}

	@Test
	public void testSystemIdentifiers() {

		FilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of());

		// id == "1" && _creationTimeUnix >= "36" && _lastUpdateTimeUnix <= "100"

		String vectorExpr = converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("id"), new Value("1")),
						new Expression(GTE, new Key("_creationTimeUnix"), new Value("36"))),
				new Expression(LTE, new Key("_lastUpdateTimeUnix"), new Value("100"))));

		assertThat(format(vectorExpr)).isEqualTo("""
				operator:And
				operands:[{operator:And
				operands:[{path:["id"]
				operator:Equal
				valueText:"1" },
				{path:["_creationTimeUnix"]
				operator:GreaterThanEqual
				valueText:"36" }]},
				{path:["_lastUpdateTimeUnix"]
				operator:LessThanEqual
				valueText:"100" }]
				""");
	}

	@Test
	public void testEQ() {
		FilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("country"));

		// country == "BG"
		String vectorExpr = converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(format(vectorExpr)).isEqualTo("""
				path:["meta_country"]
				operator:Equal
				valueText:"BG"
				""");
	}

	@Test
	public void tesEqAndGte() {
		FilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("genre", "year"));

		// genre == "drama" AND year >= 2020
		String vectorExpr = converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(format(vectorExpr)).isEqualTo("""
				operator:And
				operands:[{path:["meta_genre"]
				operator:Equal
				valueText:"drama" },
				{path:["meta_year"]
				operator:GreaterThanEqual
				valueNumber:2020 }]
				""");
	}

	@Test
	public void tesIn() {
		FilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("genre"));

		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(format(vectorExpr)).isEqualTo("""
				operator:Or
				operands:[{path:["meta_genre"]
				operator:Equal
				valueText:"comedy" },
				{operator:Or
				operands:[{path:["meta_genre"]
				operator:Equal
				valueText:"documentary" },
				{path:["meta_genre"]
				operator:Equal
				valueText:"drama" }]}]
				""");
	}

	@Test
	public void testNe() {
		FilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("city", "year", "country"));

		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String vectorExpr = converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(format(vectorExpr)).isEqualTo("""
				operator:Or
				operands:[{path:["meta_year"]
				operator:GreaterThanEqual
				valueNumber:2020 },
				{operator:And
				operands:[{path:["meta_country"]
				operator:Equal
				valueText:"BG" },
				{path:["meta_city"]
				operator:NotEqual
				valueText:"Sofia" }]}]
				""");
	}

	@Test
	public void testGroup() {
		FilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("city", "year", "country"));

		// (year >= 2020 OR country == "BG") AND city NIN ["Sofia", "Plovdiv"]
		String vectorExpr = converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NIN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));

		assertThat(format(vectorExpr)).isEqualTo("""
				operator:And
				operands:[{operator:And
				operands:[{path:["id"]
				operator:NotEqual
				valueText:"-1" },
				{operator:Or
				operands:[{path:["meta_year"]
				operator:GreaterThanEqual
				valueNumber:2020 },
				{path:["meta_country"]
				operator:Equal
				valueText:"BG" }]}]},
				{operator:And
				operands:[{path:["meta_city"]
				operator:NotEqual
				valueText:"Sofia" },
				{path:["meta_city"]
				operator:NotEqual
				valueText:"Plovdiv" }]}]
				""");
	}

	@Test
	public void tesBoolean() {
		FilterExpressionConverter converter = new WeaviateFilterExpressionConverter(
				List.of("isOpen", "year", "country"));

		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(format(vectorExpr)).isEqualTo("""
				operator:And
				operands:[{operator:And
				operands:[{path:["meta_isOpen"]
				operator:Equal
				valueBoolean:true },
				{path:["meta_year"]
				operator:GreaterThanEqual
				valueNumber:2020 }]},
				{operator:Or
				operands:[{path:["meta_country"]
				operator:Equal
				valueText:"BG" },
				{operator:Or
				operands:[{path:["meta_country"]
				operator:Equal
				valueText:"NL" },
				{path:["meta_country"]
				operator:Equal
				valueText:"US" }]}]}]
				""");
	}

	@Test
	public void testDecimal() {
		FilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("temperature"));

		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(format(vectorExpr)).isEqualTo("""
				operator:And
				operands:[{path:["meta_temperature"]
				operator:GreaterThanEqual
				valueNumber:-15.6 },
				{path:["meta_temperature"]
				operator:LessThanEqual
				valueNumber:20.13 }]
				""");
	}

	@Test
	public void testComplexIdentifiers() {
		FilterExpressionConverter converter = new WeaviateFilterExpressionConverter(List.of("country 1 2 3"));

		String vectorExpr = converter
			.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(format(vectorExpr)).isEqualTo("""
				path:["meta_country 1 2 3"]
				operator:Equal
				valueText:"BG"
				""");

		vectorExpr = converter.convertExpression(new Expression(EQ, new Key("'country 1 2 3'"), new Value("BG")));
		assertThat(format(vectorExpr)).isEqualTo("""
				path:["meta_country 1 2 3"]
				operator:Equal
				valueText:"BG"
				""");
	}

}

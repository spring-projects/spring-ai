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

package org.springframework.ai.vectorstore.neo4j.filter;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

import static org.assertj.core.api.Assertions.assertThat;
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
 * @author Gerrit Meier
 * @author Dimitrios Begnis
 */
public class Neo4jVectorFilterExpressionConverterTests {

	FilterExpressionConverter converter = new Neo4jVectorFilterExpressionConverter();

	@Test
	public void testEQ() {
		// country = "BG"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("node.`metadata.country` = \"BG\"");
	}

	@Test
	public void tesEqAndGte() {
		// genre = "drama" AND year >= 2020
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));
		assertThat(vectorExpr).isEqualTo("node.`metadata.genre` = \"drama\" AND node.`metadata.year` >= 2020");
	}

	@Test
	public void tesIn() {
		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = this.converter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("node.`metadata.genre` IN [\"comedy\",\"documentary\",\"drama\"]");
	}

	@Test
	public void tesNIn() {
		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = this.converter.convertExpression(
				new Expression(NIN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));
		assertThat(vectorExpr).isEqualTo("NOT node.`metadata.genre` IN [\"comedy\",\"documentary\",\"drama\"]");
	}

	@Test
	public void testNe() {
		// year >= 2020 OR country = "BG" AND city <> "Sofia"
		String vectorExpr = this.converter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia")))));
		assertThat(vectorExpr).isEqualTo(
				"node.`metadata.year` >= 2020 OR node.`metadata.country` = \"BG\" AND node.`metadata.city` <> \"Sofia\"");
	}

	@Test
	public void testGroup() {
		// (year >= 2020 OR country = "BG") AND NOT city IN ["Sofia", "Plovdiv"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(NOT, new Expression(IN, new Key("city"), new Value(List.of("Sofia", "Plovdiv"))))));
		assertThat(vectorExpr).isEqualTo(
				"(node.`metadata.year` >= 2020 OR node.`metadata.country` = \"BG\") AND NOT node.`metadata.city` IN [\"Sofia\",\"Plovdiv\"]");
	}

	@Test
	public void testBoolean() {
		// isOpen = true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo(
				"node.`metadata.isOpen` = true AND node.`metadata.year` >= 2020 AND node.`metadata.country` IN [\"BG\",\"NL\",\"US\"]");
	}

	@Test
	public void testDecimal() {
		// temperature >= -15.6 AND temperature <= +20.13
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(vectorExpr)
			.isEqualTo("node.`metadata.temperature` >= -15.6 AND node.`metadata.temperature` <= 20.13");
	}

	@Test
	public void testComplexIdentifiers() {
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("node.`metadata.country 1 2 3` = \"BG\"");
	}

	@Test
	public void testComplexIdentifiers2() {
		Expression expr = new FilterExpressionTextParser()
			.parse("author in ['john', 'jill'] && 'article_type' == 'blog'");
		String vectorExpr = this.converter.convertExpression(expr);
		assertThat(vectorExpr)
			.isEqualTo("node.`metadata.author` IN [\"john\",\"jill\"] AND node.`metadata.'article_type'` = \"blog\"");
	}

	@Test
	public void testEmptyList() {
		// category IN []
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("category"), new Value(List.of())));
		assertThat(vectorExpr).isEqualTo("node.`metadata.category` IN []");
	}

	@Test
	public void testSingleItemList() {
		// status IN ["active"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(IN, new Key("status"), new Value(List.of("active"))));
		assertThat(vectorExpr).isEqualTo("node.`metadata.status` IN [\"active\"]");
	}

	@Test
	public void testNullValue() {
		// description = null
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("description"), new Value(null)));
		assertThat(vectorExpr).isEqualTo("node.`metadata.description` = null");
	}

	@Test
	public void testNestedJsonPath() {
		// entity.profile.name = "EntityA"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("entity.profile.name"), new Value("EntityA")));
		assertThat(vectorExpr).isEqualTo("node.`metadata.entity.profile.name` = \"EntityA\"");
	}

	@Test
	public void testNumericStringValue() {
		// id = "1"
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("id"), new Value("1")));
		assertThat(vectorExpr).isEqualTo("node.`metadata.id` = \"1\"");
	}

	@Test
	public void testZeroValue() {
		// count = 0
		String vectorExpr = this.converter.convertExpression(new Expression(EQ, new Key("count"), new Value(0)));
		assertThat(vectorExpr).isEqualTo("node.`metadata.count` = 0");
	}

	@Test
	public void testComplexNestedGroups() {
		// ((fieldA >= 100 AND fieldB = "X1") OR (fieldA >= 50 AND fieldB = "Y2")) AND
		// fieldC <> "inactive"
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Group(new Expression(OR,
						new Group(new Expression(AND, new Expression(GTE, new Key("fieldA"), new Value(100)),
								new Expression(EQ, new Key("fieldB"), new Value("X1")))),
						new Group(new Expression(AND, new Expression(GTE, new Key("fieldA"), new Value(50)),
								new Expression(EQ, new Key("fieldB"), new Value("Y2")))))),
				new Expression(NE, new Key("fieldC"), new Value("inactive"))));

		assertThat(vectorExpr).isEqualTo("((node.`metadata.fieldA` >= 100 AND node.`metadata.fieldB` = \"X1\") OR "
				+ "(node.`metadata.fieldA` >= 50 AND node.`metadata.fieldB` = \"Y2\")) AND "
				+ "node.`metadata.fieldC` <> \"inactive\"");
	}

	@Test
	public void testMixedDataTypes() {
		// active = true AND score >= 1.5 AND tags IN ["featured", "premium"] AND version
		// = 1
		String vectorExpr = this.converter.convertExpression(new Expression(AND,
				new Expression(AND,
						new Expression(AND, new Expression(EQ, new Key("active"), new Value(true)),
								new Expression(GTE, new Key("score"), new Value(1.5))),
						new Expression(IN, new Key("tags"), new Value(List.of("featured", "premium")))),
				new Expression(EQ, new Key("version"), new Value(1))));

		assertThat(vectorExpr).isEqualTo("node.`metadata.active` = true AND node.`metadata.score` >= 1.5 AND "
				+ "node.`metadata.tags` IN [\"featured\",\"premium\"] AND node.`metadata.version` = 1");
	}

	@Test
	public void testNinWithMixedTypes() {
		// status NOT IN ["A", "B", "C"]
		String vectorExpr = this.converter
			.convertExpression(new Expression(NIN, new Key("status"), new Value(List.of("A", "B", "C"))));
		assertThat(vectorExpr).isEqualTo("NOT node.`metadata.status` IN [\"A\",\"B\",\"C\"]");
	}

	@Test
	public void testEmptyStringValue() {
		// description <> ""
		String vectorExpr = this.converter.convertExpression(new Expression(NE, new Key("description"), new Value("")));
		assertThat(vectorExpr).isEqualTo("node.`metadata.description` <> \"\"");
	}

	@Test
	public void testArrayIndexAccess() {
		// tags[0] = "important"
		String vectorExpr = this.converter
			.convertExpression(new Expression(EQ, new Key("tags[0]"), new Value("important")));
		assertThat(vectorExpr).isEqualTo("node.`metadata.tags[0]` = \"important\"");
	}

	@Test
	public void testNegativeNumbers() {
		// valueA <= -5 AND valueB >= -10
		String vectorExpr = this.converter
			.convertExpression(new Expression(AND, new Expression(LTE, new Key("valueA"), new Value(-5)),
					new Expression(GTE, new Key("valueB"), new Value(-10))));

		assertThat(vectorExpr).isEqualTo("node.`metadata.valueA` <= -5 AND node.`metadata.valueB` >= -10");
	}

}

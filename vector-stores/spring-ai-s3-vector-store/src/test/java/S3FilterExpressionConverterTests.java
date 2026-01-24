/*
 * Copyright 2025-2026 the original author or authors.
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

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.s3.S3VectorFilterSearchExpressionConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Matej Nedic
 */
class S3FilterExpressionConverterTests {

	private final S3VectorFilterSearchExpressionConverter converter = new S3VectorFilterSearchExpressionConverter();

	@Test
	public void testDate() {
		Document vectorExpr = this.converter.convertExpression(new Filter.Expression(ExpressionType.EQ,
				new Filter.Key("activationDate"), new Filter.Value(new Date(1704637752148L))));
		Document filter = Document.fromMap(
				Map.of("activationDate", Document.fromMap(Map.of("$eq", Document.fromString("2024-01-07T14:29:12Z")))));

		assertThat(vectorExpr).isEqualTo(filter);

		vectorExpr = this.converter.convertExpression(new Filter.Expression(ExpressionType.EQ,
				new Filter.Key("activationDate"), new Filter.Value("1970-01-01T00:00:02Z")));

		filter = Document.fromMap(
				Map.of("activationDate", Document.fromMap(Map.of("$eq", Document.fromString("1970-01-01T00:00:02Z")))));
		assertThat(vectorExpr).isEqualTo(filter);
	}

	@Test
	public void testEQ() {
		Document vectorExpr = this.converter.convertExpression(
				new Filter.Expression(ExpressionType.EQ, new Filter.Key("country"), new Filter.Value("BG")));

		Document filter = Document
			.fromMap(Map.of("country", Document.fromMap(Map.of("$eq", Document.fromString("BG")))));

		assertThat(vectorExpr).isEqualTo(filter);
	}

	@Test
	public void tesEqAndGte() {
		Document vectorExpr = this.converter.convertExpression(new Filter.Expression(ExpressionType.AND,
				new Filter.Expression(ExpressionType.EQ, new Filter.Key("genre"), new Filter.Value("drama")),
				new Filter.Expression(ExpressionType.GTE, new Filter.Key("year"), new Filter.Value(2020))));

		Document filter = Document.fromMap(Map.of("$and", Document.fromList(List.of(
				Document.fromMap(Map.of("genre", Document.fromMap(Map.of("$eq", Document.fromString("drama"))))),
				Document.fromMap(Map.of("year", Document.fromMap(Map.of("$gte", Document.fromNumber(2020)))))))));
		assertThat(vectorExpr).isEqualTo(filter);
	}

	@Test
	public void tesIn() {
		List<String> genres = List.of("comedy", "documentary", "drama");
		Document vectorExpr = this.converter.convertExpression(
				new Filter.Expression(ExpressionType.IN, new Filter.Key("genre"), new Filter.Value(genres)));

		Document filter = Document.fromMap(Map.of("genre", Document
			.fromMap(Map.of("$in", Document.fromList(genres.stream().map(Document::fromString).toList())))));
		assertThat(vectorExpr).isEqualTo(filter);
	}

	@Test
	public void testNe() {
		Document vectorExpr = this.converter.convertExpression(new Filter.Expression(ExpressionType.OR,
				new Filter.Expression(ExpressionType.GTE, new Filter.Key("year"), new Filter.Value(2020)),
				new Filter.Expression(ExpressionType.AND,
						new Filter.Expression(ExpressionType.EQ, new Filter.Key("country"), new Filter.Value("BG")),
						new Filter.Expression(ExpressionType.NE, new Filter.Key("city"), new Filter.Value("Sofia")))));

		Document filter = Document
			.fromMap(
					Map.of("$or",
							Document
								.fromList(
										List.of(Document.fromMap(Map
											.of("year", Document.fromMap(Map.of("$gte", Document.fromNumber(2020))))),
												Document
													.fromMap(
															Map.of("$and",
																	Document.fromList(List.of(
																			Document
																				.fromMap(Map.of("country",
																						Document.fromMap(Map.of(
																								"$eq",
																								Document.fromString(
																										"BG"))))),
																			Document.fromMap(Map.of("city", Document
																				.fromMap(Map.of("$ne", Document
																					.fromString("Sofia")))))))))))));

		assertThat(vectorExpr).isEqualTo(filter);
	}

	@Test
	public void testGroup() {
		Document vectorExpr = this.converter.convertExpression(new Filter.Expression(ExpressionType.AND,
				new Filter.Group(new Filter.Expression(ExpressionType.OR,
						new Filter.Expression(ExpressionType.GTE, new Filter.Key("year"), new Filter.Value(2020)),
						new Filter.Expression(ExpressionType.EQ, new Filter.Key("country"), new Filter.Value("BG")))),
				new Filter.Expression(ExpressionType.NIN, new Filter.Key("city"),
						new Filter.Value(List.of("Sofia", "Plovdiv")))));

		Document filter = Document
			.fromMap(
					Map.of("$and",
							Document
								.fromList(List.of(
										Document
											.fromMap(Map.of("$or",
													Document.fromList(List.of(
															Document.fromMap(Map.of("year",
																	Document.fromMap(Map.of("$gte",
																			Document.fromNumber(2020))))),
															Document.fromMap(Map.of("country",
																	Document.fromMap(Map.of("$eq",
																			Document.fromString("BG"))))))))),
										Document.fromMap(Map.of("city",
												Document.fromMap(Map.of("$nin",
														Document.fromList(List.of(Document.fromString("Sofia"),
																Document.fromString("Plovdiv")))))))))));
		assertThat(vectorExpr).isEqualTo(filter);
	}

	@Test
	public void tesBoolean() {
		Document vectorExpr = this.converter.convertExpression(new Filter.Expression(ExpressionType.AND,
				new Filter.Expression(ExpressionType.AND,
						new Filter.Expression(ExpressionType.EQ, new Filter.Key("isOpen"), new Filter.Value(true)),
						new Filter.Expression(ExpressionType.GTE, new Filter.Key("year"), new Filter.Value(2020))),
				new Filter.Expression(ExpressionType.IN, new Filter.Key("country"),
						new Filter.Value(List.of("BG", "NL", "US")))));

		Document filter = Document
			.fromMap(
					Map.of("$and",
							Document
								.fromList(List.of(
										Document
											.fromMap(Map.of("$and",
													Document.fromList(List.of(
															Document.fromMap(Map.of("isOpen",
																	Document.fromMap(Map.of("$eq",
																			Document.fromBoolean(true))))),
															Document.fromMap(Map.of("year",
																	Document.fromMap(Map.of("$gte",
																			Document.fromNumber(2020))))))))),
										Document.fromMap(Map.of("country",
												Document.fromMap(Map.of("$in",
														Document.fromList(List.of(Document.fromString("BG"),
																Document.fromString("NL"),
																Document.fromString("US")))))))))));
		assertThat(vectorExpr).isEqualTo(filter);
	}

	@Test
	public void testDecimal() {
		Document vectorExpr = this.converter.convertExpression(new Filter.Expression(ExpressionType.AND,
				new Filter.Expression(ExpressionType.GTE, new Filter.Key("temperature"), new Filter.Value(-15.6)),
				new Filter.Expression(ExpressionType.LTE, new Filter.Key("temperature"), new Filter.Value(20.13))));

		Document filter = Document.fromMap(Map.of("$and", Document.fromList(List.of(
				Document.fromMap(Map.of("temperature", Document.fromMap(Map.of("$gte", Document.fromNumber(-15.6))))),
				Document
					.fromMap(Map.of("temperature", Document.fromMap(Map.of("$lte", Document.fromNumber(20.13)))))))));

		assertThat(vectorExpr).isEqualTo(filter);
	}

	@Test
	public void testComplexIdentifiers() {
		Document vectorExpr = this.converter.convertExpression(
				new Filter.Expression(ExpressionType.EQ, new Filter.Key("\"country 1 2 3\""), new Filter.Value("BG")));
		Document filter = Document
			.fromMap(Map.of("\"country 1 2 3\"", Document.fromMap(Map.of("$eq", Document.fromString("BG")))));

		assertThat(vectorExpr).isEqualTo(filter);

		vectorExpr = this.converter.convertExpression(
				new Filter.Expression(ExpressionType.EQ, new Filter.Key("'country 1 2 3'"), new Filter.Value("BG")));
		filter = Document
			.fromMap(Map.of("'country 1 2 3'", Document.fromMap(Map.of("$eq", Document.fromString("BG")))));
		assertThat(vectorExpr).isEqualTo(filter);
	}

}

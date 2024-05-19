/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.internal.core.metadata.schema.DefaultColumnMetadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Group;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.AND;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.EQ;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.GTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.IN;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.LTE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.NE;
import static org.springframework.ai.vectorstore.filter.Filter.ExpressionType.OR;

/**
 * @author Mick Semb Wever
 * @since 1.0.0
 */
class CassandraFilterExpressionConverterTests {

	@Test
	void testEQOnPartition() {

		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(COLUMNS);

		String vectorExpr = filter.convertExpression(new Expression(EQ, new Key("id"), new Value("BG")));

		assertThat(vectorExpr).isEqualTo("\"id\" = 'BG'");
	}

	@Test
	void testEQ() {

		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(COLUMNS);

		String vectorExpr = filter.convertExpression(new Expression(EQ, new Key("country"), new Value("BG")));

		assertThat(vectorExpr).isEqualTo("\"country\" = 'BG'");
	}

	@Test
	void testNoSuchColumn() {

		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(COLUMNS);

		Assertions.assertThrows(IllegalArgumentException.class, () -> {
			filter.convertExpression(new Expression(EQ, new Key("unknown_column"), new Value("BG")));
		});
	}

	@Test
	void tesEqAndGte() {
		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(COLUMNS);

		// genre == "drama" AND year >= 2020
		String vectorExpr = filter
			.convertExpression(new Expression(AND, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(GTE, new Key("year"), new Value(2020))));

		assertThat(vectorExpr).isEqualTo("\"genre\" = 'drama' and \"year\" >= 2020");
	}

	@Test
	void tesOr() {
		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(COLUMNS);

		// genre == "drama" OR year = 2020
		String vectorExpr = filter
			.convertExpression(new Expression(OR, new Expression(EQ, new Key("genre"), new Value("drama")),
					new Expression(EQ, new Key("year"), new Value(2020))));

		assertThat(vectorExpr).isEqualTo("\"genre\" = 'drama' or \"year\" = 2020");
	}

	@Test
	void tesIn() {
		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(COLUMNS);

		// genre in ["comedy", "documentary", "drama"]
		String vectorExpr = filter.convertExpression(
				new Expression(IN, new Key("genre"), new Value(List.of("comedy", "documentary", "drama"))));

		assertThat(vectorExpr).isEqualTo("\"genre\" IN ('comedy','documentary','drama')");
	}

	@Test
	void testNe() {
		Set<ColumnMetadata> columns = new HashSet(COLUMNS);

		columns.add(new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("city"), DataTypes.TEXT, false));

		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(columns);

		// year >= 2020 OR country == "BG" AND city != "Sofia"
		String vectorExpr = filter
			.convertExpression(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
					new Group(new Expression(AND, new Expression(EQ, new Key("country"), new Value("BG")),
							new Expression(NE, new Key("city"), new Value("Sofia"))))));

		assertThat(vectorExpr).isEqualTo("\"year\" >= 2020 or \"country\" = 'BG' and \"city\" != 'Sofia'");
	}

	@Test
	void testGroup() {
		Set<ColumnMetadata> columns = new HashSet(COLUMNS);

		columns.add(new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("city"), DataTypes.TEXT, false));

		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(columns);

		// (year >= 2020 OR country == "BG") AND city IN ["Sofia", "Plovdiv"]
		String vectorExpr = filter.convertExpression(new Expression(AND,
				new Group(new Expression(OR, new Expression(GTE, new Key("year"), new Value(2020)),
						new Expression(EQ, new Key("country"), new Value("BG")))),
				new Expression(IN, new Key("city"), new Value(List.of("Sofia", "Plovdiv")))));

		assertThat(vectorExpr).isEqualTo("\"year\" >= 2020 or \"country\" = 'BG' and \"city\" IN ('Sofia','Plovdiv')");
	}

	@Test
	void tesBoolean() {
		Set<ColumnMetadata> columns = new HashSet(COLUMNS);

		columns.add(new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("isOpen"), DataTypes.BOOLEAN, false));

		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(columns);

		// isOpen == true AND year >= 2020 AND country IN ["BG", "NL", "US"]
		String vectorExpr = filter.convertExpression(new Expression(AND,
				new Expression(AND, new Expression(EQ, new Key("isOpen"), new Value(true)),
						new Expression(GTE, new Key("year"), new Value(2020))),
				new Expression(IN, new Key("country"), new Value(List.of("BG", "NL", "US")))));

		assertThat(vectorExpr).isEqualTo("\"isOpen\" = true and \"year\" >= 2020 and \"country\" IN ('BG','NL','US')");
	}

	@Test
	void testDecimal() {
		Set<ColumnMetadata> columns = new HashSet(COLUMNS);

		columns
			.add(new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("temperature"), DataTypes.DOUBLE, false));

		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(columns);

		// temperature >= -15.6 && temperature <= +20.13
		String vectorExpr = filter
			.convertExpression(new Expression(AND, new Expression(GTE, new Key("temperature"), new Value(-15.6)),
					new Expression(LTE, new Key("temperature"), new Value(20.13))));

		assertThat(vectorExpr).isEqualTo("\"temperature\" >= -15.6 and \"temperature\" <= 20.13");
	}

	@Test
	void testComplexIdentifiers() {
		Set<ColumnMetadata> columns = new HashSet(COLUMNS);

		columns.add(new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("\"country 1 2 3\""), DataTypes.TEXT,
				false));

		columns
			.add(new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("'country 1 2 3'"), DataTypes.TEXT, false));

		CassandraFilterExpressionConverter filter = new CassandraFilterExpressionConverter(columns);

		String vectorExpr = filter.convertExpression(new Expression(EQ, new Key("\"country 1 2 3\""), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("\"\"\"country 1 2 3\"\"\" = 'BG'");

		vectorExpr = filter.convertExpression(new Expression(EQ, new Key("'country 1 2 3'"), new Value("BG")));
		assertThat(vectorExpr).isEqualTo("\"'country 1 2 3'\" = 'BG'");
	}

	private static final CqlIdentifier T = CqlIdentifier.fromInternal("test");

	private static final Collection<ColumnMetadata> COLUMNS = Set.of(
			new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("id"), DataTypes.TEXT, false),
			new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("content"), DataTypes.TEXT, false),
			new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("country"), DataTypes.TEXT, false),
			new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("genre"), DataTypes.TEXT, false),
			new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("drama"), DataTypes.TEXT, false),
			new DefaultColumnMetadata(T, T, CqlIdentifier.fromInternal("year"), DataTypes.SMALLINT, false));

}

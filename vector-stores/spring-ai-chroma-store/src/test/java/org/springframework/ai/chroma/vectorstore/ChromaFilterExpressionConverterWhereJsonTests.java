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

package org.springframework.ai.chroma.vectorstore;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * metadata keys must JSON-escape when building Chroma {@code where} JSON (same
 * serialization as
 * {@link org.springframework.ai.chroma.vectorstore.ChromaFilterExpressionConverter} /
 * Pinecone converter). Ensures {@link ChromaApi#where(String)} parses a single top-level
 * property.
 */
class ChromaFilterExpressionConverterWhereJsonTests {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	@Test
	void quotedKeyWithEmbeddedJsonSyntaxParsesAsSingleWhereProperty() throws Exception {
		Expression expression = new FilterExpressionTextParser().parse("'x\" : { \"$or\": [ {} ] }, \"y' == 'ignored'");
		String whereClause = new ChromaFilterExpressionConverter().convertExpression(expression);

		ChromaApi chromaApi = ChromaApi.builder().baseUrl("http://localhost").jsonMapper(JSON_MAPPER).build();
		Map<String, Object> where = chromaApi.where(whereClause);

		assertThat(where).hasSize(1);
		assertThat(where).containsKey("x\" : { \"$or\": [ {} ] }, \"y");
		@SuppressWarnings("unchecked")
		Map<String, Object> inner = (Map<String, Object>) where.get("x\" : { \"$or\": [ {} ] }, \"y");
		assertThat(inner).containsEntry("$eq", "ignored");
	}

}

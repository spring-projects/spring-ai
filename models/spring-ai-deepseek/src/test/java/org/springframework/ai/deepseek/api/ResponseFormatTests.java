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

package org.springframework.ai.deepseek.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ResponseFormat} JSON mapping.
 *
 * @author Prathap Thotapalle
 */
class ResponseFormatTests {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	@Test
	void deserializesFromJson() {
		ResponseFormat responseFormat = this.jsonMapper.readValue("{\"type\":\"json_object\"}", ResponseFormat.class);

		assertThat(responseFormat.getType()).isEqualTo(ResponseFormat.Type.JSON_OBJECT);
	}

	@Test
	void roundTripsThroughJson() {
		ResponseFormat original = ResponseFormat.builder().type(ResponseFormat.Type.TEXT).build();

		String json = this.jsonMapper.writeValueAsString(original);

		assertThat(json).isEqualTo("{\"type\":\"text\"}");
		assertThat(this.jsonMapper.readValue(json, ResponseFormat.class)).isEqualTo(original);
	}

}

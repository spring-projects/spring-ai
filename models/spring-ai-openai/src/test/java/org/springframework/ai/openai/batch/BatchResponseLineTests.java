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

package org.springframework.ai.openai.batch;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BatchResponseLine} JSON deserialization.
 *
 * @author Yasin Akbas
 */
class BatchResponseLineTests {

	private final ObjectMapper objectMapper = new ObjectMapper();

	// CHECKSTYLE.OFF
	@Test
	void shouldDeserializeSuccessResponse() throws Exception {
		String json = """
				{
				  "id": "batch_req_abc123",
				  "custom_id": "entity-1::chat-handler",
				  "response": {
				    "status_code": 200,
				    "request_id": "req_xyz",
				    "body": {
				      "id": "chatcmpl-123",
				      "choices": [{"message": {"content": "Hello!"}}]
				    }
				  }
				}
				""";

		BatchResponseLine line = this.objectMapper.readValue(json, BatchResponseLine.class);

		assertThat(line.id()).isEqualTo("batch_req_abc123");
		assertThat(line.customId()).isEqualTo("entity-1::chat-handler");
		assertThat(line.isSuccess()).isTrue();
		assertThat(line.response()).isNotNull();
		assertThat(line.response().statusCode()).isEqualTo(200);
		assertThat(line.response().requestId()).isEqualTo("req_xyz");
		assertThat(line.response().body()).containsKey("id");
		assertThat(line.error()).isNull();
	}

	@Test
	void shouldDeserializeErrorResponse() throws Exception {
		String json = """
				{
				  "id": "batch_req_err456",
				  "custom_id": "entity-2::embed-handler",
				  "error": {
				    "code": "rate_limit_exceeded",
				    "message": "Too many requests"
				  }
				}
				""";

		BatchResponseLine line = this.objectMapper.readValue(json, BatchResponseLine.class);

		assertThat(line.id()).isEqualTo("batch_req_err456");
		assertThat(line.customId()).isEqualTo("entity-2::embed-handler");
		assertThat(line.isSuccess()).isFalse();
		assertThat(line.error()).isNotNull();
		assertThat(line.error().code()).isEqualTo("rate_limit_exceeded");
		assertThat(line.error().message()).isEqualTo("Too many requests");
	}

	@Test
	void shouldDeserializeResponseWithNon200StatusCode() throws Exception {
		String json = """
				{
				  "id": "batch_req_500",
				  "custom_id": "entity-3::handler",
				  "response": {
				    "status_code": 500,
				    "body": {"error": {"message": "Internal error"}}
				  }
				}
				""";

		BatchResponseLine line = this.objectMapper.readValue(json, BatchResponseLine.class);

		assertThat(line.isSuccess()).isFalse();
		assertThat(line.response().statusCode()).isEqualTo(500);
	}

	@Test
	void shouldHandleUnknownFields() throws Exception {
		String json = """
				{
				  "id": "batch_req_unknown",
				  "custom_id": "entity-4::handler",
				  "unknown_field": "should be ignored",
				  "response": {
				    "status_code": 200,
				    "body": {}
				  }
				}
				""";

		BatchResponseLine line = this.objectMapper.readValue(json, BatchResponseLine.class);
		assertThat(line.id()).isEqualTo("batch_req_unknown");
		assertThat(line.isSuccess()).isTrue();
	}
	// CHECKSTYLE.ON

	@Test
	void shouldSerializeRequestLine() throws Exception {
		BatchRequestLine line = BatchRequestLine.post("entity-1::handler", "/v1/chat/completions",
				Map.of("model", "gpt-4o-mini", "messages", java.util.List.of(Map.of("role", "user", "content", "Hi"))));

		String json = this.objectMapper.writeValueAsString(line);

		assertThat(json).contains("\"custom_id\":\"entity-1::handler\"");
		assertThat(json).contains("\"method\":\"POST\"");
		assertThat(json).contains("\"url\":\"/v1/chat/completions\"");
		assertThat(json).contains("\"model\":\"gpt-4o-mini\"");
	}

}

/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.util.json.autoconfigure;

import java.time.LocalDate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import org.springframework.ai.util.json.JsonParser;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration tests for {@link JsonParserObjectMapperAutoConfiguration} with tool calling
 * scenarios.
 *
 * @author Daniel Albuquerque
 */
class JsonParserIntegrationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JsonParserObjectMapperAutoConfiguration.class));

	@Test
	void shouldHandleUnescapedControlCharsInToolArguments() {
		this.contextRunner.withPropertyValues("spring.ai.json.allow-unescaped-control-chars=true").run(context -> {
			ObjectMapper mapper = context.getBean("jsonParserObjectMapper", ObjectMapper.class);

			// Simulate tool arguments with unescaped newlines (as might come from an
			// LLM)
			String jsonWithNewlines = """
					{
						"text": "Line 1
					Line 2"
					}
					""";

			// This should not throw JsonParseException
			assertThatNoException().isThrownBy(() -> mapper.readValue(jsonWithNewlines, ToolRequest.class));

			ToolRequest request = mapper.readValue(jsonWithNewlines, ToolRequest.class);
			assertThat(request.text).contains("\n");
		});
	}

	@Test
	void shouldSerializeDatesAsStringsNotArrays() {
		this.contextRunner.withPropertyValues("spring.ai.json.write-dates-as-timestamps=false").run(context -> {
			ObjectMapper mapper = context.getBean("jsonParserObjectMapper", ObjectMapper.class);

			ToolRequestWithDate request = new ToolRequestWithDate();
			request.date = LocalDate.of(2025, 7, 3);
			request.text = "Test";

			String json = mapper.writeValueAsString(request);

			// Should be "2025-07-03" not [2025,7,3]
			assertThat(json).doesNotContain("[2025");
			assertThat(json).contains("2025-07-03");
		});
	}

	@Test
	void shouldUseConfiguredMapperInStaticJsonParserMethods() {
		this.contextRunner.withPropertyValues("spring.ai.json.write-dates-as-timestamps=false").run(context -> {
			// Force bean creation to set the configured mapper
			context.getBean(JsonParser.class);

			ToolRequestWithDate request = new ToolRequestWithDate();
			request.date = LocalDate.of(2025, 7, 3);
			request.text = "Test";

			// Static method should use the Spring-configured mapper
			String json = JsonParser.toJson(request);

			assertThat(json).doesNotContain("[2025");
			assertThat(json).contains("2025-07-03");
		});
	}

	@Test
	void shouldAllowCustomMapperOverride() {
		this.contextRunner.withUserConfiguration(CustomMapperConfig.class).run(context -> {
			ObjectMapper mapper = context.getBean("jsonParserObjectMapper", ObjectMapper.class);

			// Test unescaped control chars
			String jsonWithNewlines = """
					{
						"text": "Line 1
					Line 2"
					}
					""";

			assertThatNoException().isThrownBy(() -> mapper.readValue(jsonWithNewlines, ToolRequest.class));

			// Test date serialization
			ToolRequestWithDate request = new ToolRequestWithDate();
			request.date = LocalDate.of(2025, 7, 3);
			request.text = "Test";

			String json = mapper.writeValueAsString(request);
			assertThat(json).doesNotContain("[2025");
			assertThat(json).contains("2025-07-03");
		});
	}

	@Test
	void shouldWorkWithComplexToolArguments() throws JsonProcessingException {
		this.contextRunner
			.withPropertyValues("spring.ai.json.allow-unescaped-control-chars=true",
					"spring.ai.json.write-dates-as-timestamps=false")
			.run(context -> {
				ObjectMapper mapper = context.getBean("jsonParserObjectMapper", ObjectMapper.class);

				String complexJson = """
						{
							"text": "Multi
						line
						text",
							"date": "2025-07-03"
						}
						""";

				ToolRequestWithDate request = mapper.readValue(complexJson, ToolRequestWithDate.class);
				assertThat(request.text).contains("\n");
				assertThat(request.date).isEqualTo(LocalDate.of(2025, 7, 3));

				// Round-trip
				String serialized = mapper.writeValueAsString(request);
				ToolRequestWithDate deserialized = mapper.readValue(serialized, ToolRequestWithDate.class);
				assertThat(deserialized.text).isEqualTo(request.text);
				assertThat(deserialized.date).isEqualTo(request.date);
			});
	}

	@Configuration
	static class CustomMapperConfig {

		@Bean(name = "jsonParserObjectMapper")
		public ObjectMapper jsonParserObjectMapper() {
			return JsonMapper.builder()
				.enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
				.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.addModules(org.springframework.ai.util.JacksonUtils.instantiateAvailableModules())
				.build();
		}

	}

	static class ToolRequest {

		public String text;

	}

	static class ToolRequestWithDate {

		public String text;

		public LocalDate date;

	}

}

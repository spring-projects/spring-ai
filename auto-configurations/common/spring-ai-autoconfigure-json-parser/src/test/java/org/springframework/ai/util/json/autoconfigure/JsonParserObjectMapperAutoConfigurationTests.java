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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JsonParserObjectMapperAutoConfiguration}.
 *
 * @author Daniel Albuquerque
 */
class JsonParserObjectMapperAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JsonParserObjectMapperAutoConfiguration.class));

	@Test
	void shouldAutoConfigureObjectMapper() {
		this.contextRunner.run(context -> {
			assertThat(context).hasBean("jsonParserObjectMapper");
			assertThat(context).hasBean("modelOptionsObjectMapper");
			ObjectMapper jsonParserMapper = context.getBean("jsonParserObjectMapper", ObjectMapper.class);
			assertThat(jsonParserMapper).isNotNull();
		});
	}

	@Test
	void shouldAutoConfigureJsonParser() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(JsonParser.class);
			JsonParser jsonParser = context.getBean(JsonParser.class);
			assertThat(jsonParser).isNotNull();
		});
	}

	@Test
	void shouldAllowCustomObjectMapper() {
		this.contextRunner.withUserConfiguration(CustomObjectMapperConfig.class).run(context -> {
			assertThat(context).hasBean("jsonParserObjectMapper");
			assertThat(context).hasBean("modelOptionsObjectMapper");
			ObjectMapper mapper = context.getBean("jsonParserObjectMapper", ObjectMapper.class);
			assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();
		});
	}

	@Test
	void shouldApplyPropertiesConfiguration() {
		this.contextRunner
			.withPropertyValues("spring.ai.json.allow-unescaped-control-chars=true",
					"spring.ai.json.write-dates-as-timestamps=false", "spring.ai.json.accept-empty-string-as-null=true",
					"spring.ai.json.coerce-empty-enum-strings=true")
			.run(context -> {
				JsonParserProperties properties = context.getBean(JsonParserProperties.class);
				assertThat(properties.isAllowUnescapedControlChars()).isTrue();
				assertThat(properties.isWriteDatesAsTimestamps()).isFalse();
				assertThat(properties.isAcceptEmptyStringAsNull()).isTrue();
				assertThat(properties.isCoerceEmptyEnumStrings()).isTrue();
			});
	}

	@Test
	void shouldNotWriteDatesAsTimestampsWhenPropertyIsSet() {
		this.contextRunner.withPropertyValues("spring.ai.json.write-dates-as-timestamps=false").run(context -> {
			ObjectMapper mapper = context.getBean("jsonParserObjectMapper", ObjectMapper.class);
			assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isFalse();

			// Verify date serialization format
			LocalDate date = LocalDate.of(2025, 7, 3);
			String json = mapper.writeValueAsString(date);
			// Should be "2025-07-03" not [2025,7,3]
			assertThat(json).doesNotContain("[");
			assertThat(json).contains("2025");
		});
	}

	@Test
	void shouldAllowUnescapedControlCharsWhenPropertyIsSet() {
		this.contextRunner.withPropertyValues("spring.ai.json.allow-unescaped-control-chars=true").run(context -> {
			ObjectMapper mapper = context.getBean("jsonParserObjectMapper", ObjectMapper.class);
			// Note: ALLOW_UNESCAPED_CONTROL_CHARS is a JsonReadFeature, not easily
			// testable via mapper.isEnabled()
			// The actual test for this feature is in the integration tests
			assertThat(mapper).isNotNull();
		});
	}

	@Test
	void shouldAutoConfigureModelOptionsObjectMapper() {
		this.contextRunner.run(context -> {
			assertThat(context).hasBean("modelOptionsObjectMapper");
			ObjectMapper mapper = context.getBean("modelOptionsObjectMapper", ObjectMapper.class);
			assertThat(mapper).isNotNull();
			assertThat(mapper.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)).isTrue();
		});
	}

	@Test
	void shouldConfigureEnumCoercionForModelOptions() throws JsonProcessingException {
		this.contextRunner.run(context -> {
			ObjectMapper mapper = context.getBean("modelOptionsObjectMapper", ObjectMapper.class);

			// Test enum coercion - empty string should be null
			String jsonWithEmptyEnum = "{\"status\":\"\"}";
			TestEnumClass result = mapper.readValue(jsonWithEmptyEnum, TestEnumClass.class);
			assertThat(result.status).isNull();
		});
	}

	@Test
	void shouldUseConfiguredMapperInModelOptionsUtils() {
		this.contextRunner.run(context -> {
			// Trigger bean creation to initialize ModelOptionsUtils
			context.getBean("modelOptionsUtilsInitializer");

			// Test that ModelOptionsUtils uses the Spring-configured mapper
			TestObject obj = new TestObject();
			obj.name = "test";

			String json = ModelOptionsUtils.toJsonString(obj);
			assertThat(json).contains("test");

			TestObject result = ModelOptionsUtils.jsonToObject(json, TestObject.class);
			assertThat(result.name).isEqualTo("test");
		});
	}

	@Test
	void shouldApplyModelOptionsProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.json.accept-empty-string-as-null=false",
					"spring.ai.json.coerce-empty-enum-strings=false")
			.run(context -> {
				ObjectMapper mapper = context.getBean("modelOptionsObjectMapper", ObjectMapper.class);
				assertThat(mapper.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)).isFalse();
			});
	}

	@Configuration
	static class CustomObjectMapperConfig {

		@Bean(name = "jsonParserObjectMapper")
		public ObjectMapper jsonParserObjectMapper() {
			return com.fasterxml.jackson.databind.json.JsonMapper.builder()
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
		}

	}

	static class TestObject {

		public String name;

	}

	static class TestEnumClass {

		public TestStatus status;

	}

	enum TestStatus {

		ACTIVE, INACTIVE

	}

}

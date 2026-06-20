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

package org.springframework.ai.model.tool.autoconfigure;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link ToolCallingAutoConfiguration} exposes an overridable named
 * {@code springAiJsonMapper} bean and installs it as the {@link JacksonUtils} default
 * mapper used across Spring AI's JSON parsing.
 */
class JsonMapperAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class));

	private JsonMapper originalDefaultMapper;

	@BeforeEach
	void captureDefaultMapper() {
		this.originalDefaultMapper = JacksonUtils.getDefaultJsonMapper();
	}

	@AfterEach
	void restoreDefaultMapper() {
		JacksonUtils.setDefaultJsonMapper(this.originalDefaultMapper);
	}

	@Test
	void defaultMapperIsExposedAsNamedBeanAndInstalled() {
		this.contextRunner.run(context -> {
			JsonMapper bean = context.getBean("springAiJsonMapper", JsonMapper.class);
			assertThat(JacksonUtils.getDefaultJsonMapper()).isSameAs(bean);
		});
	}

	@Test
	void customSpringAiJsonMapperOverridesDefault() {
		this.contextRunner.withUserConfiguration(LenientMapperConfig.class).run(context -> {
			JsonMapper custom = context.getBean("springAiJsonMapper", JsonMapper.class);

			assertThat(custom).isSameAs(LenientMapperConfig.INSTANCE);
			assertThat(JacksonUtils.getDefaultJsonMapper()).isSameAs(custom);
		});
	}

	@Test
	void overrideReachesStaticJsonHelperConsumers() {
		// JsonParser delegates to a static-final JsonHelper built at class-load time. A
		// JSON value with a raw newline (an unescaped control char) is rejected by the
		// default mapper but accepted once the lenient override is installed, proving the
		// override is picked up lazily.
		String jsonWithRawNewline = "{\"text\":\"line1\nline2\"}";

		this.contextRunner.withUserConfiguration(LenientMapperConfig.class).run(context -> {
			@SuppressWarnings("unchecked")
			Map<String, Object> parsed = JsonParser.fromJson(jsonWithRawNewline, Map.class);
			assertThat(parsed).containsEntry("text", "line1\nline2");
		});
	}

	@Configuration
	static class LenientMapperConfig {

		static final JsonMapper INSTANCE = JsonMapper.builder()
			.enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
			.addModules(JacksonUtils.instantiateAvailableModules())
			.build();

		@Bean
		JsonMapper springAiJsonMapper() {
			return INSTANCE;
		}

	}

}

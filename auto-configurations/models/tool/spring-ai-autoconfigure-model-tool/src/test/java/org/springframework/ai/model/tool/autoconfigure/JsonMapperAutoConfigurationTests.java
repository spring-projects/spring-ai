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

import org.junit.jupiter.api.Test;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link ToolCallingAutoConfiguration} exposes overridable named
 * {@link JsonMapper} beans and wires them into the static {@link JsonParser} and
 * {@link ModelOptionsUtils} utilities.
 */
class JsonMapperAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class));

	@Test
	void defaultMappersAreExposedAsNamedBeansAndWiredIntoStatics() {
		this.contextRunner.run(context -> {
			JsonMapper jsonParserMapper = context.getBean("jsonParserJsonMapper", JsonMapper.class);
			JsonMapper modelOptionsMapper = context.getBean("modelOptionsJsonMapper", JsonMapper.class);

			assertThat(JsonParser.getJsonMapper()).isSameAs(jsonParserMapper);
			assertThat(ModelOptionsUtils.getJsonMapper()).isSameAs(modelOptionsMapper);
		});
	}

	@Test
	void customJsonParserJsonMapperOverridesDefault() {
		this.contextRunner.withUserConfiguration(CustomJsonParserMapperConfig.class).run(context -> {
			JsonMapper custom = context.getBean("jsonParserJsonMapper", JsonMapper.class);

			assertThat(custom).isSameAs(CustomJsonParserMapperConfig.INSTANCE);
			assertThat(JsonParser.getJsonMapper()).isSameAs(custom);
		});
	}

	@Test
	void customModelOptionsJsonMapperOverridesDefault() {
		this.contextRunner.withUserConfiguration(CustomModelOptionsMapperConfig.class).run(context -> {
			JsonMapper custom = context.getBean("modelOptionsJsonMapper", JsonMapper.class);

			assertThat(custom).isSameAs(CustomModelOptionsMapperConfig.INSTANCE);
			assertThat(ModelOptionsUtils.getJsonMapper()).isSameAs(custom);
		});
	}

	@Configuration
	static class CustomJsonParserMapperConfig {

		static final JsonMapper INSTANCE = JsonMapper.builder()
			.enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
			.build();

		@Bean
		JsonMapper jsonParserJsonMapper() {
			return INSTANCE;
		}

	}

	@Configuration
	static class CustomModelOptionsMapperConfig {

		static final JsonMapper INSTANCE = JsonMapper.builder().build();

		@Bean
		JsonMapper modelOptionsJsonMapper() {
			return INSTANCE;
		}

	}

}

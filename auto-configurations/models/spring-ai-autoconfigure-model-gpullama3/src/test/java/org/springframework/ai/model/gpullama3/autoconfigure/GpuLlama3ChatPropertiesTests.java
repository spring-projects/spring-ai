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

package org.springframework.ai.model.gpullama3.autoconfigure;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.springframework.ai.gpullama3.GpuLlama3ChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GpuLlama3ChatPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesConfiguration.class);

	@Test
	void bindsDefaultsAndConvertsToOptions() {
		this.contextRunner.withPropertyValues("spring.ai.gpullama3.chat.model-path=/models/llama.gguf").run(context -> {
			GpuLlama3ChatProperties properties = context.getBean(GpuLlama3ChatProperties.class);

			assertThat(properties.isEnabled()).isTrue();
			assertThat(properties.getModelPath()).isEqualTo(Path.of("/models/llama.gguf"));
			assertThat(properties.getModel()).isNull();
			assertThat(properties.getOnGpu()).isFalse();
			assertThat(properties.getContextLength()).isEqualTo(2048);
			assertThat(properties.getOptions().getMaxTokens()).isEqualTo(512);
			assertThat(properties.getOptions().getTemperature()).isEqualTo(0.1);
			assertThat(properties.getOptions().getTopP()).isEqualTo(1.0);
			assertThat(properties.getOptions().getSeed()).isEqualTo(12345L);

			GpuLlama3ChatOptions options = properties.toOptions();
			assertThat(options.getModelPath()).isEqualTo(Path.of("/models/llama.gguf"));
			assertThat(options.getModel()).isNull();
			assertThat(options.getOnGpu()).isFalse();
			assertThat(options.getContextLength()).isEqualTo(2048);
			assertThat(options.getMaxTokens()).isEqualTo(512);
			assertThat(options.getTemperature()).isEqualTo(0.1);
			assertThat(options.getTopP()).isEqualTo(1.0);
			assertThat(options.getSeed()).isEqualTo(12345L);
		});
	}

	@Test
	void bindsConfiguredValuesAndProviderOptions() {
		this.contextRunner.withPropertyValues("spring.ai.gpullama3.chat.enabled=false",
				"spring.ai.gpullama3.chat.model-path=/models/llama.gguf", "spring.ai.gpullama3.chat.model=llama-3.2",
				"spring.ai.gpullama3.chat.on-gpu=true", "spring.ai.gpullama3.chat.context-length=4096",
				"spring.ai.gpullama3.chat.options.max-tokens=64", "spring.ai.gpullama3.chat.options.temperature=0.25",
				"spring.ai.gpullama3.chat.options.top-p=0.8", "spring.ai.gpullama3.chat.options.seed=9000000000",
				"spring.ai.gpullama3.chat.options.stop-sequences=stop,halt",
				"spring.ai.gpullama3.chat.options.top-k=40", "spring.ai.gpullama3.chat.options.frequency-penalty=0.1",
				"spring.ai.gpullama3.chat.options.presence-penalty=0.2")
			.run(context -> {
				GpuLlama3ChatProperties properties = context.getBean(GpuLlama3ChatProperties.class);

				assertThat(properties.isEnabled()).isFalse();
				GpuLlama3ChatOptions options = properties.toOptions();
				assertThat(options.getModelPath()).isEqualTo(Path.of("/models/llama.gguf"));
				assertThat(options.getModel()).isEqualTo("llama-3.2");
				assertThat(options.getOnGpu()).isTrue();
				assertThat(options.getContextLength()).isEqualTo(4096);
				assertThat(options.getMaxTokens()).isEqualTo(64);
				assertThat(options.getTemperature()).isEqualTo(0.25);
				assertThat(options.getTopP()).isEqualTo(0.8);
				assertThat(options.getSeed()).isEqualTo(9_000_000_000L);
				assertThat(options.getStopSequences()).containsExactly("stop", "halt");
				assertThat(options.getTopK()).isEqualTo(40);
				assertThat(options.getFrequencyPenalty()).isEqualTo(0.1);
				assertThat(options.getPresencePenalty()).isEqualTo(0.2);
			});
	}

	@Test
	void rejectsMissingModelPathWhenConvertedToOptions() {
		GpuLlama3ChatProperties properties = new GpuLlama3ChatProperties();

		assertThatThrownBy(properties::toOptions).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("spring.ai.gpullama3.chat.model-path");
	}

	@Test
	void rejectsInvalidContextLengthWhenConvertedToOptions() {
		GpuLlama3ChatProperties properties = new GpuLlama3ChatProperties();
		properties.setModelPath(Path.of("/models/llama.gguf"));
		properties.setContextLength(0);

		assertThatThrownBy(properties::toOptions).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("spring.ai.gpullama3.chat.context-length");
	}

	@Test
	void rejectsInvalidMaxTokensWhenConvertedToOptions() {
		GpuLlama3ChatProperties properties = new GpuLlama3ChatProperties();
		properties.setModelPath(Path.of("/models/llama.gguf"));
		properties.getOptions().setMaxTokens(0);

		assertThatThrownBy(properties::toOptions).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("spring.ai.gpullama3.chat.options.max-tokens");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(GpuLlama3ChatProperties.class)
	static class PropertiesConfiguration {

	}

}

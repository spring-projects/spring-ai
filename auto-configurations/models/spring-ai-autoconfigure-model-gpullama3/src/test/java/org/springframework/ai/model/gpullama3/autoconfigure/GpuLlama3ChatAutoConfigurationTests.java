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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.gpullama3.GpuLlama3ChatModel;
import org.springframework.ai.gpullama3.GpuLlama3ChatOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GpuLlama3ChatAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(GpuLlama3ChatAutoConfiguration.class));

	@Test
	void createsOptionsWhenGpuLlama3ChatModelIsSelected() {
		this.contextRunner.withUserConfiguration(ExistingChatModelConfiguration.class)
			.withPropertyValues("spring.ai.model.chat=gpullama3",
					"spring.ai.gpullama3.chat.model-path=/models/llama.gguf", "spring.ai.gpullama3.chat.model=llama",
					"spring.ai.gpullama3.chat.on-gpu=false", "spring.ai.gpullama3.chat.context-length=1024",
					"spring.ai.gpullama3.chat.options.max-tokens=64")
			.run(context -> {
				assertThat(context).hasSingleBean(GpuLlama3ChatOptions.class);
				GpuLlama3ChatOptions options = context.getBean(GpuLlama3ChatOptions.class);
				assertThat(options.getModelPath()).isEqualTo(Path.of("/models/llama.gguf"));
				assertThat(options.getModel()).isEqualTo("llama");
				assertThat(options.getOnGpu()).isFalse();
				assertThat(options.getContextLength()).isEqualTo(1024);
				assertThat(options.getMaxTokens()).isEqualTo(64);
				assertThat(context).doesNotHaveBean(GpuLlama3ChatModel.class);
			});
	}

	@Test
	void createsOptionsWhenChatModelPropertyIsMissing() {
		this.contextRunner.withUserConfiguration(ExistingChatModelConfiguration.class)
			.withPropertyValues("spring.ai.gpullama3.chat.model-path=/models/llama.gguf")
			.run(context -> assertThat(context).hasSingleBean(GpuLlama3ChatOptions.class));
	}

	@Test
	void backsOffWhenModelPathIsMissing() {
		this.contextRunner.withPropertyValues("spring.ai.model.chat=gpullama3").run(context -> {
			assertThat(context).doesNotHaveBean(GpuLlama3ChatProperties.class);
			assertThat(context).doesNotHaveBean(GpuLlama3ChatOptions.class);
			assertThat(context).doesNotHaveBean(GpuLlama3ChatModel.class);
		});
	}

	@Test
	void backsOffWhenModelPathIsMissingAndChatModelBeanExists() {
		this.contextRunner.withUserConfiguration(ExistingChatModelConfiguration.class)
			.withPropertyValues("spring.ai.model.chat=gpullama3")
			.run(context -> {
				assertThat(context).hasSingleBean(ChatModel.class);
				assertThat(context).doesNotHaveBean(GpuLlama3ChatProperties.class);
				assertThat(context).doesNotHaveBean(GpuLlama3ChatOptions.class);
				assertThat(context).doesNotHaveBean(GpuLlama3ChatModel.class);
			});
	}

	@Test
	void backsOffWhenDifferentChatModelIsSelected() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.gpullama3.chat.model-path=/models/llama.gguf")
			.run(context -> {
				assertThat(context).doesNotHaveBean(GpuLlama3ChatProperties.class);
				assertThat(context).doesNotHaveBean(GpuLlama3ChatOptions.class);
				assertThat(context).doesNotHaveBean(GpuLlama3ChatModel.class);
			});
	}

	@Test
	void backsOffWhenDisabled() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.chat=gpullama3", "spring.ai.gpullama3.chat.enabled=false",
					"spring.ai.gpullama3.chat.model-path=/models/llama.gguf")
			.run(context -> {
				assertThat(context).doesNotHaveBean(GpuLlama3ChatProperties.class);
				assertThat(context).doesNotHaveBean(GpuLlama3ChatOptions.class);
				assertThat(context).doesNotHaveBean(GpuLlama3ChatModel.class);
			});
	}

	@Test
	void backsOffFromChatModelWhenChatModelBeanExists() {
		this.contextRunner.withUserConfiguration(ExistingChatModelConfiguration.class)
			.withPropertyValues("spring.ai.model.chat=gpullama3",
					"spring.ai.gpullama3.chat.model-path=/models/llama.gguf")
			.run(context -> {
				assertThat(context).hasSingleBean(ChatModel.class);
				assertThat(context).hasSingleBean(GpuLlama3ChatOptions.class);
				assertThat(context).doesNotHaveBean(GpuLlama3ChatModel.class);
			});
	}

	@Test
	void backsOffFromOptionsWhenOptionsBeanExists() {
		this.contextRunner
			.withUserConfiguration(ExistingChatOptionsConfiguration.class, ExistingChatModelConfiguration.class)
			.withPropertyValues("spring.ai.model.chat=gpullama3",
					"spring.ai.gpullama3.chat.model-path=/models/configured.gguf")
			.run(context -> {
				assertThat(context).hasSingleBean(GpuLlama3ChatOptions.class);
				GpuLlama3ChatOptions options = context.getBean(GpuLlama3ChatOptions.class);
				assertThat(options.getModelPath()).isEqualTo(Path.of("/models/custom.gguf"));
			});
	}

	@Test
	void registersAutoConfigurationImport() throws IOException {
		try (InputStream inputStream = getClass().getClassLoader()
			.getResourceAsStream("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {

			assertThat(inputStream).isNotNull();
			String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			assertThat(imports).contains(GpuLlama3ChatAutoConfiguration.class.getName());
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class ExistingChatModelConfiguration {

		@Bean
		ChatModel chatModel() {
			return prompt -> new ChatResponse(
					List.of(new Generation(AssistantMessage.builder().content("stub").build())));
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class ExistingChatOptionsConfiguration {

		@Bean
		GpuLlama3ChatOptions gpullama3ChatOptions() {
			return GpuLlama3ChatOptions.builder().modelPath(Path.of("/models/custom.gguf")).build();
		}

	}

}

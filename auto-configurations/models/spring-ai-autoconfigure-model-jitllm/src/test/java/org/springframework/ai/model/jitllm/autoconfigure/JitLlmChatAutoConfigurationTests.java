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

package org.springframework.ai.model.jitllm.autoconfigure;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.beehive.jitllm.api.ThinkingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.jitllm.JitLlmChatModel;
import org.springframework.ai.jitllm.JitLlmChatOptions;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JitLlmChatAutoConfiguration} and {@link JitLlmChatProperties}.
 * None of them loads a model.
 *
 * @author Michalis Papadimitriou
 */
class JitLlmChatAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withConfiguration(
			AutoConfigurations.of(ToolCallingAutoConfiguration.class, JitLlmChatAutoConfiguration.class));

	@Test
	void noModelIsCreatedWithoutAModelPath() {
		this.contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(JitLlmChatModel.class);
			assertThat(context).doesNotHaveBean(JitLlmChatProperties.class);
		});
	}

	@Test
	void noModelIsCreatedWhenAnotherChatModelIsSelected() {
		this.contextRunner
			.withPropertyValues("spring.ai.model.chat=none", "spring.ai.jitllm.chat.model-path=/models/model.gguf")
			.run(context -> assertThat(context).doesNotHaveBean(JitLlmChatModel.class));
	}

	@Test
	void aModelUrlAlsoActivatesTheModelAndIsDownloadedIntoTheCacheDirectory(@TempDir Path cache) {
		// Nothing listens on the discard port, so the download fails at startup: proof
		// that the model-url alone activated the auto-configuration and reached the
		// downloader.
		this.contextRunner
			.withPropertyValues("spring.ai.jitllm.chat.model-url=http://127.0.0.1:9/models/model.gguf",
					"spring.ai.jitllm.chat.cache-directory=" + cache)
			.run(context -> assertThat(context).getFailure()
				.hasMessageContaining("Downloading http://127.0.0.1:9/models/model.gguf failed"));
	}

	@Test
	void modelSourceAndThinkingPropertiesBind() {
		Map<String, String> source = Map.of("spring.ai.jitllm.chat.model-url", "hf://owner/repo/model.gguf",
				"spring.ai.jitllm.chat.cache-directory", "/var/cache/models",
				"spring.ai.jitllm.chat.hugging-face-token", "secret", "spring.ai.jitllm.chat.thinking", "disabled");

		JitLlmChatProperties properties = new Binder(new MapConfigurationPropertySource(source))
			.bind(JitLlmChatProperties.CONFIG_PREFIX, JitLlmChatProperties.class)
			.get();

		assertThat(properties.getModelUrl()).isEqualTo("hf://owner/repo/model.gguf");
		assertThat(properties.getCacheDirectory()).isEqualTo(Path.of("/var/cache/models"));
		assertThat(properties.getHuggingFaceToken()).isEqualTo("secret");
		assertThat(properties.getThinking()).isEqualTo(ThinkingMode.DISABLED);
	}

	@Test
	void propertiesBindAndBecomeTheDefaultOptions() {
		Map<String, String> source = Map.of("spring.ai.jitllm.chat.model-path", "/models/qwen3.gguf",
				"spring.ai.jitllm.chat.model-name", "qwen3", "spring.ai.jitllm.chat.on-gpu", "true",
				"spring.ai.jitllm.chat.context-length", "4096", "spring.ai.jitllm.chat.max-tokens", "256",
				"spring.ai.jitllm.chat.temperature", "0.3", "spring.ai.jitllm.chat.top-p", "0.9",
				"spring.ai.jitllm.chat.seed", "42", "spring.ai.jitllm.chat.stop-sequences[0]", "END");

		JitLlmChatProperties properties = new Binder(new MapConfigurationPropertySource(source))
			.bind(JitLlmChatProperties.CONFIG_PREFIX, JitLlmChatProperties.class)
			.get();

		assertThat(properties.getModelPath()).isEqualTo(Path.of("/models/qwen3.gguf"));
		assertThat(properties.isOnGpu()).isTrue();
		assertThat(properties.getContextLength()).isEqualTo(4096);
		JitLlmChatOptions options = properties.toOptions();
		assertThat(options.getModel()).isEqualTo("qwen3");
		assertThat(options.getMaxTokens()).isEqualTo(256);
		assertThat(options.getTemperature()).isEqualTo(0.3);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getSeed()).isEqualTo(42L);
		assertThat(options.getStopSequences()).isEqualTo(List.of("END"));
	}

	@Test
	void theDefaultsLeaveSamplingToTheEngineAndRunOnTheCpu() {
		JitLlmChatProperties properties = new JitLlmChatProperties();

		assertThat(properties.isOnGpu()).isFalse();
		assertThat(properties.getThinking()).isEqualTo(ThinkingMode.DEFAULT);
		assertThat(properties.getContextLength()).isZero();
		assertThat(properties.toOptions()).isEqualTo(JitLlmChatOptions.builder().build());
	}

}

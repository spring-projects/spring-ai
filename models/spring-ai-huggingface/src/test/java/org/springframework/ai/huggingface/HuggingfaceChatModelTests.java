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

package org.springframework.ai.huggingface;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.retry.RetryUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HuggingfaceChatModel}.
 *
 * @author Myeongdeok Kang
 */
@ExtendWith(MockitoExtension.class)
class HuggingfaceChatModelTests {

	@Mock
	HuggingfaceApi huggingfaceApi;

	@Mock
	ToolCallingManager toolCallingManager;

	@Mock
	ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	@Test
	void buildHuggingfaceChatModelWithConstructor() {
		ChatModel chatModel = new HuggingfaceChatModel(this.huggingfaceApi,
				HuggingfaceChatOptions.builder().model("meta-llama/Llama-3.2-3B-Instruct").build(),
				this.toolCallingManager, ObservationRegistry.NOOP, RetryUtils.DEFAULT_RETRY_TEMPLATE,
				this.toolExecutionEligibilityPredicate);
		assertThat(chatModel).isNotNull();
	}

	@Test
	void buildHuggingfaceChatModelWithBuilder() {
		ChatModel chatModel = HuggingfaceChatModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityPredicate(this.toolExecutionEligibilityPredicate)
			.build();
		assertThat(chatModel).isNotNull();
	}

	@Test
	void buildHuggingfaceChatModelWithNullApi() {
		assertThatThrownBy(() -> HuggingfaceChatModel.builder()
			.huggingfaceApi(null)
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityPredicate(this.toolExecutionEligibilityPredicate)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("huggingfaceApi must not be null");
	}

	@Test
	void buildHuggingfaceChatModelWithAllBuilderOptions() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.temperature(0.7)
			.maxTokens(100)
			.topP(0.9)
			.build();

		ChatModel chatModel = HuggingfaceChatModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(options)
			.toolCallingManager(this.toolCallingManager)
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.observationRegistry(ObservationRegistry.NOOP)
			.toolExecutionEligibilityPredicate(this.toolExecutionEligibilityPredicate)
			.build();

		assertThat(chatModel).isNotNull();
		assertThat(chatModel).isInstanceOf(HuggingfaceChatModel.class);
	}

	@Test
	void buildHuggingfaceChatModelWithCustomObservationRegistry() {
		ObservationRegistry customRegistry = ObservationRegistry.create();

		ChatModel chatModel = HuggingfaceChatModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityPredicate(this.toolExecutionEligibilityPredicate)
			.observationRegistry(customRegistry)
			.build();

		assertThat(chatModel).isNotNull();
	}

	@Test
	void buildHuggingfaceChatModelImmutability() {
		// Test that the builder creates immutable instances
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.temperature(0.5)
			.build();

		ChatModel chatModel1 = HuggingfaceChatModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(options)
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityPredicate(this.toolExecutionEligibilityPredicate)
			.build();

		ChatModel chatModel2 = HuggingfaceChatModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(options)
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityPredicate(this.toolExecutionEligibilityPredicate)
			.build();

		// Should create different instances
		assertThat(chatModel1).isNotSameAs(chatModel2);
		assertThat(chatModel1).isNotNull();
		assertThat(chatModel2).isNotNull();
	}

	@Test
	void buildHuggingfaceChatModelWithMinimalConfiguration() {
		// Test building with only required parameters
		ChatModel chatModel = HuggingfaceChatModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityPredicate(this.toolExecutionEligibilityPredicate)
			.build();

		assertThat(chatModel).isNotNull();
		assertThat(chatModel).isInstanceOf(HuggingfaceChatModel.class);
	}

	@Test
	void getDefaultOptionsReturnsCopy() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.temperature(0.7)
			.build();

		HuggingfaceChatModel chatModel = HuggingfaceChatModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(options)
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityPredicate(this.toolExecutionEligibilityPredicate)
			.build();

		HuggingfaceChatOptions retrievedOptions = (HuggingfaceChatOptions) chatModel.getDefaultOptions();
		assertThat(retrievedOptions).isNotNull();
		assertThat(retrievedOptions).isNotSameAs(options);
		assertThat(retrievedOptions.getModel()).isEqualTo(options.getModel());
		assertThat(retrievedOptions.getTemperature()).isEqualTo(options.getTemperature());
	}

	@Test
	void setObservationConventionValidation() {
		HuggingfaceChatModel chatModel = HuggingfaceChatModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.toolCallingManager(this.toolCallingManager)
			.toolExecutionEligibilityPredicate(this.toolExecutionEligibilityPredicate)
			.build();

		assertThatThrownBy(() -> chatModel.setObservationConvention(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("observationConvention cannot be null");
	}

	@Test
	void buildHuggingfaceChatModelWithNullDefaultOptions() {
		assertThatThrownBy(() -> new HuggingfaceChatModel(this.huggingfaceApi, null, this.toolCallingManager,
				ObservationRegistry.NOOP, RetryUtils.DEFAULT_RETRY_TEMPLATE, this.toolExecutionEligibilityPredicate))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("defaultOptions must not be null");
	}

	@Test
	void buildHuggingfaceChatModelWithNullObservationRegistry() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.build();

		assertThatThrownBy(() -> new HuggingfaceChatModel(this.huggingfaceApi, options, this.toolCallingManager, null,
				RetryUtils.DEFAULT_RETRY_TEMPLATE, this.toolExecutionEligibilityPredicate))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("observationRegistry must not be null");
	}

	@Test
	void buildHuggingfaceChatModelWithNullRetryTemplate() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.build();

		assertThatThrownBy(() -> new HuggingfaceChatModel(this.huggingfaceApi, options, this.toolCallingManager,
				ObservationRegistry.NOOP, null, this.toolExecutionEligibilityPredicate))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("retryTemplate must not be null");
	}

}

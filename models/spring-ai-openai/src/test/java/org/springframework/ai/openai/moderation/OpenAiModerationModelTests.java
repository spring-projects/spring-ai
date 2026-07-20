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

package org.springframework.ai.openai.moderation;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.moderations.ModerationCreateParams;
import com.openai.models.moderations.ModerationCreateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.moderation.ModerationOptions;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.OpenAiModerationOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OpenAiModerationModel.
 *
 * @author Ilayaperumal Gopinathan
 * @author guan xu
 */
@ExtendWith(MockitoExtension.class)
class OpenAiModerationModelTests {

	@Mock
	private OpenAIClient mockClient;

	@Test
	void testModelCreation() {
		OpenAiModerationModel model = OpenAiModerationModel.builder().openAiClient(this.mockClient).build();
		assertThat(model).isNotNull();
		assertThat(model.getOptions()).isNotNull();
	}

	@Test
	void testBuilderWithDefaults() {
		OpenAiModerationModel model = OpenAiModerationModel.builder().openAiClient(this.mockClient).build();

		assertThat(model).isNotNull();
		assertThat(model.getOptions()).isNotNull();
		assertThat(model.getOptions()).isInstanceOf(OpenAiModerationOptions.class);

		OpenAiModerationOptions defaults = model.getOptions();
		assertThat(defaults.getModel()).isEqualTo(OpenAiModerationOptions.DEFAULT_MODERATION_MODEL);
	}

	@Test
	void testBuilderWithCustomOptions() {
		OpenAiModerationOptions options = OpenAiModerationOptions.builder().model("text-moderation-stable").build();

		OpenAiModerationModel model = OpenAiModerationModel.builder()
			.openAiClient(this.mockClient)
			.options(options)
			.build();

		assertThat(model).isNotNull();
		assertThat(model.getOptions().getModel()).isEqualTo("text-moderation-stable");
	}

	@Test
	void testBuilderWithNullClient() {
		OpenAiModerationModel model = OpenAiModerationModel.builder()
			.options(OpenAiModerationOptions.builder().apiKey("test-key").build())
			.build();
		assertThat(model).isNotNull();
		assertThat(model.getOptions()).isNotNull();
	}

	@Test
	void testMutateCreatesBuilderWithSameConfiguration() {
		OpenAiModerationOptions options = OpenAiModerationOptions.builder()
			.model("text-moderation-latest")
			.baseUrl("https://custom.example.com")
			.build();

		OpenAiModerationModel model = OpenAiModerationModel.builder()
			.openAiClient(this.mockClient)
			.options(options)
			.build();

		OpenAiModerationModel mutatedModel = model.mutate().build();

		assertThat(mutatedModel).isNotNull();
		assertThat(mutatedModel.getOptions().getModel()).isEqualTo("text-moderation-latest");
	}

	@Test
	void testMutateAllowsOverridingOptions() {
		OpenAiModerationOptions options = OpenAiModerationOptions.builder().model("text-moderation-stable").build();

		OpenAiModerationModel model = OpenAiModerationModel.builder()
			.openAiClient(this.mockClient)
			.options(options)
			.build();

		OpenAiModerationOptions newOptions = OpenAiModerationOptions.builder().model("omni-moderation-latest").build();

		OpenAiModerationModel mutatedModel = model.mutate().options(newOptions).build();

		assertThat(mutatedModel.getOptions().getModel()).isEqualTo("omni-moderation-latest");
		assertThat(model.getOptions().getModel()).isEqualTo("text-moderation-stable");
	}

	@Test
	void testOptionsBuilder() {
		OpenAiModerationOptions options = OpenAiModerationOptions.builder()
			.model("omni-moderation-latest")
			.baseUrl("https://api.example.com")
			.apiKey("test-key")
			.organizationId("org-123")
			.timeout(Duration.ofSeconds(30))
			.maxRetries(5)
			.build();

		assertThat(options.getModel()).isEqualTo("omni-moderation-latest");
		assertThat(options.getBaseUrl()).isEqualTo("https://api.example.com");
		assertThat(options.getApiKey()).isEqualTo("test-key");
		assertThat(options.getOrganizationId()).isEqualTo("org-123");
		assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(30));
		assertThat(options.getMaxRetries()).isEqualTo(5);
	}

	@Test
	void testOptionsFrom() {
		OpenAiModerationOptions original = OpenAiModerationOptions.builder()
			.model("text-moderation-stable")
			.baseUrl("https://api.example.com")
			.apiKey("test-key")
			.organizationId("org-123")
			.build();

		OpenAiModerationOptions copied = OpenAiModerationOptions.builder().from(original).build();

		assertThat(copied.getModel()).isEqualTo(original.getModel());
		assertThat(copied.getBaseUrl()).isEqualTo(original.getBaseUrl());
		assertThat(copied.getApiKey()).isEqualTo(original.getApiKey());
		assertThat(copied.getOrganizationId()).isEqualTo(original.getOrganizationId());
	}

	@Test
	void testOptionsMerge() {
		OpenAiModerationOptions target = OpenAiModerationOptions.builder().model("text-moderation-stable").build();

		ModerationOptions source = new ModerationOptions() {
			@Override
			public String getModel() {
				return "omni-moderation-latest";
			}
		};

		OpenAiModerationOptions merged = OpenAiModerationOptions.builder().from(target).merge(source).build();

		assertThat(merged.getModel()).isEqualTo("omni-moderation-latest");
	}

	@Test
	void testOptionsMergeWithNull() {
		OpenAiModerationOptions target = OpenAiModerationOptions.builder().model("text-moderation-stable").build();

		OpenAiModerationOptions merged = OpenAiModerationOptions.builder().from(target).merge(null).build();

		assertThat(merged.getModel()).isEqualTo("text-moderation-stable");
	}

	@Test
	void testOptionsEqualsAndHashCode() {
		OpenAiModerationOptions options1 = OpenAiModerationOptions.builder()
			.model("omni-moderation-latest")
			.baseUrl("https://api.example.com")
			.build();

		OpenAiModerationOptions options2 = OpenAiModerationOptions.builder()
			.model("omni-moderation-latest")
			.baseUrl("https://api.example.com")
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testOptionsNotEquals() {
		OpenAiModerationOptions options1 = OpenAiModerationOptions.builder().model("omni-moderation-latest").build();

		OpenAiModerationOptions options2 = OpenAiModerationOptions.builder().model("text-moderation-stable").build();

		assertThat(options1).isNotEqualTo(options2);
	}

	@Test
	void testDefaultModelValue() {
		assertThat(OpenAiModerationOptions.DEFAULT_MODERATION_MODEL).isEqualTo("omni-moderation-latest");
	}

	@Test
	void testOptionsGetModelWithNullInternalValue() {
		OpenAiModerationOptions options = OpenAiModerationOptions.builder().build();
		assertThat(options.getModel()).isEqualTo(OpenAiModerationOptions.DEFAULT_MODERATION_MODEL);
	}

	@Test
	void testOptionsBuilderMergeCustomHeaders() {
		OpenAiModerationOptions defaultOptions = OpenAiModerationOptions.builder()
			.customHeaders(Map.of("default-header", "default-value"))
			.build();

		OpenAiModerationOptions requestOptions = OpenAiModerationOptions.builder()
			.customHeaders(Map.of("merged-header1", "merged-value1", "merged-header2", "merged-value2"))
			.build();

		OpenAiModerationOptions mergedOptions = OpenAiModerationOptions.builder()
			.from(defaultOptions)
			.merge(requestOptions)
			.build();

		assertThat(mergedOptions.getCustomHeaders()).containsEntry("default-header", "default-value")
			.containsEntry("merged-header1", "merged-value1")
			.containsEntry("merged-header2", "merged-value2");
	}

	@Test
	void testPropagatesTimeoutFromRequestOptions() {
		Duration expectedTimeout = Duration.ofSeconds(30);

		OpenAIClient mockClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
		when(mockClient.moderations().create(any(ModerationCreateParams.class), any(RequestOptions.class))).thenReturn(
				ModerationCreateResponse.builder().id("TEST_ID").model("TEST_MODEL").results(List.of()).build());

		OpenAiModerationModel model = OpenAiModerationModel.builder().openAiClient(mockClient).build();

		OpenAiModerationOptions options = OpenAiModerationOptions.builder().timeout(expectedTimeout).build();

		model.call(new ModerationPrompt("hi", options));

		ArgumentCaptor<RequestOptions> argumentCaptor = ArgumentCaptor.forClass(RequestOptions.class);
		verify(mockClient.moderations()).create(any(ModerationCreateParams.class), argumentCaptor.capture());
		RequestOptions value = argumentCaptor.getValue();
		assertThat(value.getTimeout()).isNotNull();
		assertThat(value.getTimeout().request()).isEqualTo(expectedTimeout);
	}

}

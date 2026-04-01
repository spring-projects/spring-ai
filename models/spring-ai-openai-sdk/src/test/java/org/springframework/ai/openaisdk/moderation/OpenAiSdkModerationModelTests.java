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

package org.springframework.ai.openaisdk.moderation;

import java.time.Duration;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.moderation.ModerationOptions;
import org.springframework.ai.openaisdk.OpenAiSdkModerationModel;
import org.springframework.ai.openaisdk.OpenAiSdkModerationOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OpenAiSdkModerationModel.
 *
 * @author Ilayaperumal Gopinathan
 */
@ExtendWith(MockitoExtension.class)
class OpenAiSdkModerationModelTests {

	@Mock
	private OpenAIClient mockClient;

	@Test
	void testModelCreation() {
		OpenAiSdkModerationModel model = OpenAiSdkModerationModel.builder().openAiClient(this.mockClient).build();
		assertThat(model).isNotNull();
		assertThat(model.getOptions()).isNotNull();
	}

	@Test
	void testBuilderWithDefaults() {
		OpenAiSdkModerationModel model = OpenAiSdkModerationModel.builder().openAiClient(this.mockClient).build();

		assertThat(model).isNotNull();
		assertThat(model.getOptions()).isNotNull();
		assertThat(model.getOptions()).isInstanceOf(OpenAiSdkModerationOptions.class);

		OpenAiSdkModerationOptions defaults = model.getOptions();
		assertThat(defaults.getModel()).isEqualTo(OpenAiSdkModerationOptions.DEFAULT_MODERATION_MODEL);
	}

	@Test
	void testBuilderWithCustomOptions() {
		OpenAiSdkModerationOptions options = OpenAiSdkModerationOptions.builder()
			.model("text-moderation-stable")
			.build();

		OpenAiSdkModerationModel model = OpenAiSdkModerationModel.builder()
			.openAiClient(this.mockClient)
			.options(options)
			.build();

		assertThat(model).isNotNull();
		assertThat(model.getOptions().getModel()).isEqualTo("text-moderation-stable");
	}

	@Test
	void testBuilderWithNullClient() {
		OpenAiSdkModerationModel model = OpenAiSdkModerationModel.builder()
			.options(OpenAiSdkModerationOptions.builder().apiKey("test-key").build())
			.build();
		assertThat(model).isNotNull();
		assertThat(model.getOptions()).isNotNull();
	}

	@Test
	void testMutateCreatesBuilderWithSameConfiguration() {
		OpenAiSdkModerationOptions options = OpenAiSdkModerationOptions.builder()
			.model("text-moderation-latest")
			.baseUrl("https://custom.example.com")
			.build();

		OpenAiSdkModerationModel model = OpenAiSdkModerationModel.builder()
			.openAiClient(this.mockClient)
			.options(options)
			.build();

		OpenAiSdkModerationModel mutatedModel = model.mutate().build();

		assertThat(mutatedModel).isNotNull();
		assertThat(mutatedModel.getOptions().getModel()).isEqualTo("text-moderation-latest");
	}

	@Test
	void testMutateAllowsOverridingOptions() {
		OpenAiSdkModerationOptions options = OpenAiSdkModerationOptions.builder()
			.model("text-moderation-stable")
			.build();

		OpenAiSdkModerationModel model = OpenAiSdkModerationModel.builder()
			.openAiClient(this.mockClient)
			.options(options)
			.build();

		OpenAiSdkModerationOptions newOptions = OpenAiSdkModerationOptions.builder()
			.model("omni-moderation-latest")
			.build();

		OpenAiSdkModerationModel mutatedModel = model.mutate().options(newOptions).build();

		assertThat(mutatedModel.getOptions().getModel()).isEqualTo("omni-moderation-latest");
		assertThat(model.getOptions().getModel()).isEqualTo("text-moderation-stable");
	}

	@Test
	void testOptionsBuilder() {
		OpenAiSdkModerationOptions options = OpenAiSdkModerationOptions.builder()
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
		OpenAiSdkModerationOptions original = OpenAiSdkModerationOptions.builder()
			.model("text-moderation-stable")
			.baseUrl("https://api.example.com")
			.apiKey("test-key")
			.organizationId("org-123")
			.build();

		OpenAiSdkModerationOptions copied = OpenAiSdkModerationOptions.builder().from(original).build();

		assertThat(copied.getModel()).isEqualTo(original.getModel());
		assertThat(copied.getBaseUrl()).isEqualTo(original.getBaseUrl());
		assertThat(copied.getApiKey()).isEqualTo(original.getApiKey());
		assertThat(copied.getOrganizationId()).isEqualTo(original.getOrganizationId());
	}

	@Test
	void testOptionsMerge() {
		OpenAiSdkModerationOptions target = OpenAiSdkModerationOptions.builder()
			.model("text-moderation-stable")
			.build();

		ModerationOptions source = new ModerationOptions() {
			@Override
			public String getModel() {
				return "omni-moderation-latest";
			}
		};

		OpenAiSdkModerationOptions merged = OpenAiSdkModerationOptions.builder().from(target).merge(source).build();

		assertThat(merged.getModel()).isEqualTo("omni-moderation-latest");
	}

	@Test
	void testOptionsMergeWithNull() {
		OpenAiSdkModerationOptions target = OpenAiSdkModerationOptions.builder()
			.model("text-moderation-stable")
			.build();

		OpenAiSdkModerationOptions merged = OpenAiSdkModerationOptions.builder().from(target).merge(null).build();

		assertThat(merged.getModel()).isEqualTo("text-moderation-stable");
	}

	@Test
	void testOptionsCopy() {
		OpenAiSdkModerationOptions original = OpenAiSdkModerationOptions.builder()
			.model("omni-moderation-latest")
			.baseUrl("https://api.example.com")
			.build();

		OpenAiSdkModerationOptions copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getModel()).isEqualTo(original.getModel());
		assertThat(copy.getBaseUrl()).isEqualTo(original.getBaseUrl());
	}

	@Test
	void testOptionsEqualsAndHashCode() {
		OpenAiSdkModerationOptions options1 = OpenAiSdkModerationOptions.builder()
			.model("omni-moderation-latest")
			.baseUrl("https://api.example.com")
			.build();

		OpenAiSdkModerationOptions options2 = OpenAiSdkModerationOptions.builder()
			.model("omni-moderation-latest")
			.baseUrl("https://api.example.com")
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testOptionsNotEquals() {
		OpenAiSdkModerationOptions options1 = OpenAiSdkModerationOptions.builder()
			.model("omni-moderation-latest")
			.build();

		OpenAiSdkModerationOptions options2 = OpenAiSdkModerationOptions.builder()
			.model("text-moderation-stable")
			.build();

		assertThat(options1).isNotEqualTo(options2);
	}

	@Test
	void testOptionsToString() {
		OpenAiSdkModerationOptions options = OpenAiSdkModerationOptions.builder()
			.model("omni-moderation-latest")
			.baseUrl("https://api.example.com")
			.build();

		String string = options.toString();
		assertThat(string).contains("omni-moderation-latest");
		assertThat(string).contains("https://api.example.com");
	}

	@Test
	void testDefaultModelValue() {
		assertThat(OpenAiSdkModerationOptions.DEFAULT_MODERATION_MODEL).isEqualTo("omni-moderation-latest");
	}

	@Test
	void testOptionsGetModelWithNullInternalValue() {
		OpenAiSdkModerationOptions options = OpenAiSdkModerationOptions.builder().build();
		assertThat(options.getModel()).isEqualTo(OpenAiSdkModerationOptions.DEFAULT_MODERATION_MODEL);
	}

}

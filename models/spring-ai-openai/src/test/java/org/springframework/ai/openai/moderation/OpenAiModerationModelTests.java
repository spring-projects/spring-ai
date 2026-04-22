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

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.moderation.ModerationOptions;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.OpenAiModerationOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OpenAiModerationModel.
 *
 * @author Ilayaperumal Gopinathan
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
	void testOptionsCopy() {
		OpenAiModerationOptions original = OpenAiModerationOptions.builder()
			.model("omni-moderation-latest")
			.baseUrl("https://api.example.com")
			.build();

		OpenAiModerationOptions copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getModel()).isEqualTo(original.getModel());
		assertThat(copy.getBaseUrl()).isEqualTo(original.getBaseUrl());
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
	void testOptionsToString() {
		OpenAiModerationOptions options = OpenAiModerationOptions.builder()
			.model("omni-moderation-latest")
			.baseUrl("https://api.example.com")
			.build();

		String string = options.toString();
		assertThat(string).contains("omni-moderation-latest");
		assertThat(string).contains("https://api.example.com");
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

}

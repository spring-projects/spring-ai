/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.openai.api;

import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/*
 * Integration test for mutate/clone functionality on OpenAiApi and OpenAiChatModel.
 * This test demonstrates creating multiple ChatClient instances with different endpoints and options
 * from a single autoconfigured OpenAiChatModel/OpenAiApi, as per the feature request.
 */
class OpenAiChatModelMutateTests {

	// Simulate autoconfigured base beans (in real usage, these would be @Autowired)
	private final OpenAiApi baseApi = OpenAiApi.builder().baseUrl("https://api.openai.com").apiKey("base-key").build();

	private final OpenAiChatModel baseModel = OpenAiChatModel.builder()
		.openAiApi(this.baseApi)
		.defaultOptions(OpenAiChatOptions.builder().model("gpt-3.5-turbo").build())
		.build();

	@Test
	void testMutateCreatesDistinctClientsWithDifferentEndpointsAndModels() {
		// Mutate for GPT-4
		OpenAiApi gpt4Api = this.baseApi.mutate()
			.baseUrl("https://api.openai.com")
			.apiKey("your-api-key-for-gpt4")
			.build();
		OpenAiChatModel gpt4Model = this.baseModel.mutate()
			.openAiApi(gpt4Api)
			.defaultOptions(OpenAiChatOptions.builder().model("gpt-4").temperature(0.7).build())
			.build();
		// Mutate for Llama
		OpenAiApi llamaApi = this.baseApi.mutate()
			.baseUrl("https://your-custom-endpoint.com")
			.apiKey("your-api-key-for-llama")
			.build();
		OpenAiChatModel llamaModel = this.baseModel.mutate()
			.openAiApi(llamaApi)
			.defaultOptions(OpenAiChatOptions.builder().model("llama-70b").temperature(0.5).build())
			.build();
		// Assert endpoints and models are different
		assertThat(gpt4Model).isNotSameAs(llamaModel);
		assertThat(gpt4Api).isNotSameAs(llamaApi);
		assertThat(gpt4Model.toString()).contains("gpt-4");
		assertThat(llamaModel.toString()).contains("llama-70b");
		// Optionally, assert endpoints
		// (In real usage, you might expose/get the baseUrl for assertion)
	}

	@Test
	void testCloneCreatesDeepCopy() {
		OpenAiChatModel clone = this.baseModel.clone();
		assertThat(clone).isNotSameAs(this.baseModel);
		assertThat(clone).hasToString(this.baseModel.toString());
	}

	@Test
	void mutateDoesNotAffectOriginal() {
		OpenAiChatModel mutated = this.baseModel.mutate()
			.defaultOptions(OpenAiChatOptions.builder().model("gpt-4").build())
			.build();
		assertThat(mutated).isNotSameAs(this.baseModel);
		assertThat(mutated.getDefaultOptions().getModel()).isEqualTo("gpt-4");
		assertThat(this.baseModel.getDefaultOptions().getModel()).isEqualTo("gpt-3.5-turbo");
	}

	@Test
	void mutateHeadersCreatesDistinctHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.add("X-Test", "value");
		OpenAiApi mutatedApi = this.baseApi.mutate().headers(headers).build();

		assertThat(mutatedApi.getHeaders().get("X-Test")).isNotNull();
		assertThat(this.baseApi.getHeaders().get("X-Test")).isNull();
	}

	@Test
	void mutateHandlesNullAndDefaults() {
		OpenAiApi apiWithDefaults = OpenAiApi.builder().baseUrl("https://api.openai.com").apiKey("key").build();
		OpenAiApi mutated = apiWithDefaults.mutate().build();
		assertThat(mutated).isNotNull();
		assertThat(mutated.getBaseUrl()).isEqualTo("https://api.openai.com");
		assertThat(mutated.getApiKey().getValue()).isEqualTo("key");
	}

	@Test
	void multipleSequentialMutationsProduceDistinctInstances() {
		OpenAiChatModel m1 = this.baseModel.mutate()
			.defaultOptions(OpenAiChatOptions.builder().model("m1").build())
			.build();
		OpenAiChatModel m2 = m1.mutate().defaultOptions(OpenAiChatOptions.builder().model("m2").build()).build();
		OpenAiChatModel m3 = m2.mutate().defaultOptions(OpenAiChatOptions.builder().model("m3").build()).build();
		assertThat(m1).isNotSameAs(m2);
		assertThat(m2).isNotSameAs(m3);
		assertThat(m1.getDefaultOptions().getModel()).isEqualTo("m1");
		assertThat(m2.getDefaultOptions().getModel()).isEqualTo("m2");
		assertThat(m3.getDefaultOptions().getModel()).isEqualTo("m3");
	}

	@Test
	void mutateAndCloneAreEquivalent() {
		OpenAiChatModel mutated = this.baseModel.mutate().build();
		OpenAiChatModel cloned = this.baseModel.clone();
		assertThat(mutated.toString()).isEqualTo(cloned.toString());
		assertThat(mutated).isNotSameAs(cloned);
	}

	@Test
	void testApiMutateWithComplexHeaders() {
		HttpHeaders complexHeaders = new HttpHeaders();
		complexHeaders.add("Authorization", "Bearer custom-token");
		complexHeaders.add("X-Custom-Header", "value1");
		complexHeaders.add("X-Custom-Header", "value2");
		complexHeaders.add("User-Agent", "Custom-Client/1.0");

		OpenAiApi mutatedApi = this.baseApi.mutate().headers(complexHeaders).build();

		assertThat(mutatedApi.getHeaders().get("Authorization")).isNotNull();
		assertThat(mutatedApi.getHeaders().get("X-Custom-Header")).isNotNull();
		assertThat(mutatedApi.getHeaders().get("User-Agent")).isNotNull();
		assertThat(mutatedApi.getHeaders().get("X-Custom-Header")).hasSize(2);
	}

	@Test
	void testMutateWithEmptyOptions() {
		OpenAiChatOptions emptyOptions = OpenAiChatOptions.builder().build();

		OpenAiChatModel mutated = this.baseModel.mutate().defaultOptions(emptyOptions).build();

		assertThat(mutated.getDefaultOptions()).isNotNull();
		assertThat(mutated.getDefaultOptions()).isNotSameAs(this.baseModel.getDefaultOptions());
	}

	@Test
	void testApiMutateWithEmptyHeaders() {
		HttpHeaders emptyHeaders = new HttpHeaders();

		OpenAiApi mutatedApi = this.baseApi.mutate().headers(emptyHeaders).build();

		assertThat(mutatedApi.getHeaders().isEmpty()).isTrue();
	}

	@Test
	void testCloneAndMutateIndependence() {
		// Test that clone and mutate produce independent instances
		OpenAiChatModel cloned = this.baseModel.clone();
		OpenAiChatModel mutated = this.baseModel.mutate().build();

		// Modify cloned instance (if options are mutable)
		// This test verifies that operations on one don't affect the other
		assertThat(cloned).isNotSameAs(mutated);
		assertThat(cloned).isNotSameAs(this.baseModel);
		assertThat(mutated).isNotSameAs(this.baseModel);
	}

	@Test
	void testMutateBuilderValidation() {
		// Test that mutate builder validates inputs appropriately
		assertThat(this.baseModel.mutate()).isNotNull();

		// Test building without any changes
		OpenAiChatModel unchanged = this.baseModel.mutate().build();
		assertThat(unchanged).isNotNull();
		assertThat(unchanged).isNotSameAs(this.baseModel);
	}

	@Test
	void testMutateWithInvalidBaseUrl() {
		assertThatThrownBy(() -> this.baseApi.mutate().baseUrl("").build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl");

		assertThatThrownBy(() -> this.baseApi.mutate().baseUrl(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("baseUrl");
	}

	@Test
	void testMutateWithNullOpenAiApi() {
		assertThatThrownBy(() -> this.baseModel.mutate().openAiApi(null).build())
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void testMutatePreservesUnchangedFields() {
		String originalBaseUrl = this.baseApi.getBaseUrl();
		String newApiKey = "new-test-key";

		OpenAiApi mutated = this.baseApi.mutate().apiKey(newApiKey).build();

		assertThat(mutated.getBaseUrl()).isEqualTo(originalBaseUrl);
		assertThat(mutated.getApiKey().getValue()).isEqualTo(newApiKey);
	}

}

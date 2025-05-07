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

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.util.LinkedMultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Integration test for mutate/clone functionality on OpenAiApi and OpenAiChatModel.
 * This test demonstrates creating multiple ChatClient instances with different endpoints and options
 * from a single autoconfigured OpenAiChatModel/OpenAiApi, as per the feature request.
 */
class OpenAiChatModelMutateTests {

	// Simulate autoconfigured base beans (in real usage, these would be @Autowired)
	private final OpenAiApi baseApi = OpenAiApi.builder().baseUrl("https://api.openai.com").apiKey("base-key").build();

	private final OpenAiChatModel baseModel = OpenAiChatModel.builder()
		.openAiApi(baseApi)
		.defaultOptions(OpenAiChatOptions.builder().model("gpt-3.5-turbo").build())
		.build();

	@Test
	void testMutateCreatesDistinctClientsWithDifferentEndpointsAndModels() {
		// Mutate for GPT-4
		OpenAiApi gpt4Api = baseApi.mutate().baseUrl("https://api.openai.com").apiKey("your-api-key-for-gpt4").build();
		OpenAiChatModel gpt4Model = baseModel.mutate()
			.openAiApi(gpt4Api)
			.defaultOptions(OpenAiChatOptions.builder().model("gpt-4").temperature(0.7).build())
			.build();
		ChatClient gpt4Client = ChatClient.builder(gpt4Model).build();

		// Mutate for Llama
		OpenAiApi llamaApi = baseApi.mutate()
			.baseUrl("https://your-custom-endpoint.com")
			.apiKey("your-api-key-for-llama")
			.build();
		OpenAiChatModel llamaModel = baseModel.mutate()
			.openAiApi(llamaApi)
			.defaultOptions(OpenAiChatOptions.builder().model("llama-70b").temperature(0.5).build())
			.build();
		ChatClient llamaClient = ChatClient.builder(llamaModel).build();

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
		OpenAiChatModel clone = baseModel.clone();
		assertThat(clone).isNotSameAs(baseModel);
		assertThat(clone.toString()).isEqualTo(baseModel.toString());
	}

	@Test
	void mutateDoesNotAffectOriginal() {
		OpenAiChatModel mutated = baseModel.mutate()
			.defaultOptions(OpenAiChatOptions.builder().model("gpt-4").build())
			.build();
		assertThat(mutated).isNotSameAs(baseModel);
		assertThat(mutated.getDefaultOptions().getModel()).isEqualTo("gpt-4");
		assertThat(baseModel.getDefaultOptions().getModel()).isEqualTo("gpt-3.5-turbo");
	}

	@Test
	void mutateHeadersCreatesDistinctHeaders() {
		OpenAiApi mutatedApi = baseApi.mutate()
			.headers(new LinkedMultiValueMap<>(java.util.Map.of("X-Test", java.util.List.of("value"))))
			.build();

		assertThat(mutatedApi.getHeaders()).containsKey("X-Test");
		assertThat(baseApi.getHeaders()).doesNotContainKey("X-Test");
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
		OpenAiChatModel m1 = baseModel.mutate().defaultOptions(OpenAiChatOptions.builder().model("m1").build()).build();
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
		OpenAiChatModel mutated = baseModel.mutate().build();
		OpenAiChatModel cloned = baseModel.clone();
		assertThat(mutated.toString()).isEqualTo(cloned.toString());
		assertThat(mutated).isNotSameAs(cloned);
	}

}

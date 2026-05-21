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

package org.springframework.ai.openai.setup;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import com.openai.azure.credential.AzureApiKeyCredential;
import com.openai.client.OpenAIClient;
import com.openai.core.ClientOptions;
import com.openai.models.ChatModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenAiSetupTests {

	@Test
	void detectModelProvider_returnsMicrosoftFoundry_whenMicrosoftFoundryFlagIsTrue() {
		OpenAiSetup.ModelProvider result = OpenAiSetup.detectModelProvider(true, false, null, null, null);

		assertEquals(OpenAiSetup.ModelProvider.MICROSOFT_FOUNDRY, result);
	}

	@Test
	void detectModelProvider_returnsGitHubModels_whenGitHubFlagIsTrue() {
		OpenAiSetup.ModelProvider result = OpenAiSetup.detectModelProvider(false, true, null, null, null);

		assertEquals(OpenAiSetup.ModelProvider.GITHUB_MODELS, result);
	}

	@Test
	void detectModelProvider_returnsMicrosoftFoundry_whenBaseUrlMatchesAzure() {
		OpenAiSetup.ModelProvider result = OpenAiSetup.detectModelProvider(false, false,
				"https://example.openai.azure.com", null, null);

		assertEquals(OpenAiSetup.ModelProvider.MICROSOFT_FOUNDRY, result);
	}

	@Test
	void detectModelProvider_returnsGitHubModels_whenBaseUrlMatchesGitHub() {
		OpenAiSetup.ModelProvider result = OpenAiSetup.detectModelProvider(false, false,
				"https://models.github.ai/inference", null, null);

		assertEquals(OpenAiSetup.ModelProvider.GITHUB_MODELS, result);
	}

	@Test
	void detectModelProvider_returnsOpenAI_whenNoConditionsMatch() {
		OpenAiSetup.ModelProvider result = OpenAiSetup.detectModelProvider(false, false, null, null, null);

		assertEquals(OpenAiSetup.ModelProvider.OPEN_AI, result);
	}

	@Test
	void setupSyncClient_returnsClient_whenValidApiKeyProvided() {
		OpenAIClient client = OpenAiSetup.setupSyncClient(null, "valid-api-key", null, null, null, null, false, false,
				null, Duration.ofSeconds(30), 2, null, null);

		assertNotNull(client);
	}

	@Test
	void setupSyncClient_appliesCustomHeaders_whenProvided() {
		Map<String, String> customHeaders = Collections.singletonMap("X-Custom-Header", "value");

		OpenAIClient client = OpenAiSetup.setupSyncClient(null, "valid-api-key", null, null, null, null, false, false,
				null, Duration.ofSeconds(30), 2, null, customHeaders);

		assertNotNull(client);
	}

	@Test
	void calculateBaseUrl_returnsDefaultOpenAIUrl_whenBaseUrlIsNull() {
		String result = OpenAiSetup.calculateBaseUrl(null, OpenAiSetup.ModelProvider.OPEN_AI, null, null);

		assertEquals(OpenAiSetup.OPENAI_URL, result);
	}

	@Test
	void calculateBaseUrl_returnsGitHubUrl_whenModelHostIsGitHub() {
		String result = OpenAiSetup.calculateBaseUrl(null, OpenAiSetup.ModelProvider.GITHUB_MODELS, null, null);

		assertEquals(OpenAiSetup.GITHUB_MODELS_URL, result);
	}

	@Test
	void calculateBaseUrl_returnsCorrectMicrosoftFoundryUrl_whenMicrosoftFoundryEndpointProvided() {
		String endpoint = "https://xxx.openai.azure.com/openai/v1/";
		String result = OpenAiSetup.calculateBaseUrl(endpoint, OpenAiSetup.ModelProvider.MICROSOFT_FOUNDRY,
				ChatModel.GPT_5_MINI.asString(), null);

		assertEquals("https://xxx.openai.azure.com/openai/v1", result);
	}

	@Test
	void setupSyncClient_returnsClient_whenMicrosoftFoundryEndpointAndApiKeyProvided() {
		String endpoint = "https://xxx.openai.azure.com/openai/v1/";
		String apiKey = "test-foundry-api-key";
		String deploymentName = ChatModel.GPT_5_2.asString();

		OpenAIClient client = OpenAiSetup.setupSyncClient(endpoint, apiKey, null, deploymentName, null, null, true,
				false, null, Duration.ofSeconds(30), 2, null, null);

		assertNotNull(client);
	}

	@Test
	void setupSyncClient_usesApiKeyHeader_notBearerToken_forMicrosoftFoundry() throws Exception {
		OpenAIClient client = OpenAiSetup.setupSyncClient("https://my-resource.openai.azure.com/", "my-foundry-key",
				null, null, null, null, true, false, null, Duration.ofSeconds(30), 2, null, null);

		Field field = client.getClass().getDeclaredField("clientOptions");
		field.setAccessible(true);
		ClientOptions options = (ClientOptions) field.get(client);
		assertInstanceOf(AzureApiKeyCredential.class, options.credential());
		AzureApiKeyCredential credential = (AzureApiKeyCredential) options.credential();
		assertEquals("my-foundry-key", credential.apiKey());
		assertThat(options.headers().values("Authorization")).isEmpty();
	}

}

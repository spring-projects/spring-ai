package org.springframework.ai.openaisdk.setup;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenAiSdkSetupTests {

	@Test
	void detectModelProvider_returnsAzureOpenAI_whenAzureFlagIsTrue() {
		OpenAiSdkSetup.ModelProvider result = OpenAiSdkSetup.detectModelProvider(true, false, null, null, null);

		assertEquals(OpenAiSdkSetup.ModelProvider.AZURE_OPEN_AI, result);
	}

	@Test
	void detectModelProvider_returnsGitHubModels_whenGitHubFlagIsTrue() {
		OpenAiSdkSetup.ModelProvider result = OpenAiSdkSetup.detectModelProvider(false, true, null, null, null);

		assertEquals(OpenAiSdkSetup.ModelProvider.GITHUB_MODELS, result);
	}

	@Test
	void detectModelProvider_returnsAzureOpenAI_whenBaseUrlMatchesAzure() {
		OpenAiSdkSetup.ModelProvider result = OpenAiSdkSetup.detectModelProvider(false, false,
				"https://example.openai.azure.com", null, null);

		assertEquals(OpenAiSdkSetup.ModelProvider.AZURE_OPEN_AI, result);
	}

	@Test
	void detectModelProvider_returnsGitHubModels_whenBaseUrlMatchesGitHub() {
		OpenAiSdkSetup.ModelProvider result = OpenAiSdkSetup.detectModelProvider(false, false,
				"https://models.inference.ai.azure.com", null, null);

		assertEquals(OpenAiSdkSetup.ModelProvider.GITHUB_MODELS, result);
	}

	@Test
	void detectModelProvider_returnsOpenAI_whenNoConditionsMatch() {
		OpenAiSdkSetup.ModelProvider result = OpenAiSdkSetup.detectModelProvider(false, false, null, null, null);

		assertEquals(OpenAiSdkSetup.ModelProvider.OPEN_AI, result);
	}

	@Test
	void setupSyncClient_returnsClient_whenValidApiKeyProvided() {
		OpenAIClient client = OpenAiSdkSetup.setupSyncClient(null, "valid-api-key", null, null, null, null, false,
				false, null, Duration.ofSeconds(30), 2, null, null);

		assertNotNull(client);
	}

	@Test
	void setupSyncClient_appliesCustomHeaders_whenProvided() {
		Map<String, String> customHeaders = Collections.singletonMap("X-Custom-Header", "value");

		OpenAIClient client = OpenAiSdkSetup.setupSyncClient(null, "valid-api-key", null, null, null, null, false,
				false, null, Duration.ofSeconds(30), 2, null, customHeaders);

		assertNotNull(client);
	}

	@Test
	void calculateBaseUrl_returnsDefaultOpenAIUrl_whenBaseUrlIsNull() {
		String result = OpenAiSdkSetup.calculateBaseUrl(null, OpenAiSdkSetup.ModelProvider.OPEN_AI, null, null);

		assertEquals(OpenAiSdkSetup.OPENAI_URL, result);
	}

	@Test
	void calculateBaseUrl_returnsGitHubUrl_whenModelHostIsGitHub() {
		String result = OpenAiSdkSetup.calculateBaseUrl(null, OpenAiSdkSetup.ModelProvider.GITHUB_MODELS, null, null);

		assertEquals(OpenAiSdkSetup.GITHUB_MODELS_URL, result);
	}

}

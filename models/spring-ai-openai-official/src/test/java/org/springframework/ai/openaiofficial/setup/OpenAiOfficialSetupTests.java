package org.springframework.ai.openaiofficial.setup;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class OpenAiOfficialSetupTests {

	@Test
	void detectModelProvider_returnsAzureOpenAI_whenAzureFlagIsTrue() {
		OpenAiOfficialSetup.ModelProvider result = OpenAiOfficialSetup.detectModelProvider(true, false, null, null,
				null);

		assertEquals(OpenAiOfficialSetup.ModelProvider.AZURE_OPEN_AI, result);
	}

	@Test
	void detectModelProvider_returnsGitHubModels_whenGitHubFlagIsTrue() {
		OpenAiOfficialSetup.ModelProvider result = OpenAiOfficialSetup.detectModelProvider(false, true, null, null,
				null);

		assertEquals(OpenAiOfficialSetup.ModelProvider.GITHUB_MODELS, result);
	}

	@Test
	void detectModelProvider_returnsAzureOpenAI_whenBaseUrlMatchesAzure() {
		OpenAiOfficialSetup.ModelProvider result = OpenAiOfficialSetup.detectModelProvider(false, false,
				"https://example.openai.azure.com", null, null);

		assertEquals(OpenAiOfficialSetup.ModelProvider.AZURE_OPEN_AI, result);
	}

	@Test
	void detectModelProvider_returnsGitHubModels_whenBaseUrlMatchesGitHub() {
		OpenAiOfficialSetup.ModelProvider result = OpenAiOfficialSetup.detectModelProvider(false, false,
				"https://models.inference.ai.azure.com", null, null);

		assertEquals(OpenAiOfficialSetup.ModelProvider.GITHUB_MODELS, result);
	}

	@Test
	void detectModelProvider_returnsOpenAI_whenNoConditionsMatch() {
		OpenAiOfficialSetup.ModelProvider result = OpenAiOfficialSetup.detectModelProvider(false, false, null, null,
				null);

		assertEquals(OpenAiOfficialSetup.ModelProvider.OPEN_AI, result);
	}

	@Test
	void setupSyncClient_returnsClient_whenValidApiKeyProvided() {
		OpenAIClient client = OpenAiOfficialSetup.setupSyncClient(null, "valid-api-key", null, null, null, null, false,
				false, null, Duration.ofSeconds(30), 2, null, null);

		assertNotNull(client);
	}

	@Test
	void setupSyncClient_appliesCustomHeaders_whenProvided() {
		Map<String, String> customHeaders = Collections.singletonMap("X-Custom-Header", "value");

		OpenAIClient client = OpenAiOfficialSetup.setupSyncClient(null, "valid-api-key", null, null, null, null, false,
				false, null, Duration.ofSeconds(30), 2, null, customHeaders);

		assertNotNull(client);
	}

	@Test
	void calculateBaseUrl_returnsDefaultOpenAIUrl_whenBaseUrlIsNull() {
		String result = OpenAiOfficialSetup.calculateBaseUrl(null, OpenAiOfficialSetup.ModelProvider.OPEN_AI, null,
				null);

		assertEquals(OpenAiOfficialSetup.OPENAI_URL, result);
	}

	@Test
	void calculateBaseUrl_returnsGitHubUrl_whenModelHostIsGitHub() {
		String result = OpenAiOfficialSetup.calculateBaseUrl(null, OpenAiOfficialSetup.ModelProvider.GITHUB_MODELS,
				null, null);

		assertEquals(OpenAiOfficialSetup.GITHUB_MODELS_URL, result);
	}

}

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
	void detectModelHost_returnsAzureOpenAI_whenAzureFlagIsTrue() {
		OpenAiOfficialSetup.ModelHost result = OpenAiOfficialSetup.detectModelHost(true, false, null, null, null);

		assertEquals(OpenAiOfficialSetup.ModelHost.AZURE_OPENAI, result);
	}

	@Test
	void detectModelHost_returnsGitHubModels_whenGitHubFlagIsTrue() {
		OpenAiOfficialSetup.ModelHost result = OpenAiOfficialSetup.detectModelHost(false, true, null, null, null);

		assertEquals(OpenAiOfficialSetup.ModelHost.GITHUB_MODELS, result);
	}

	@Test
	void detectModelHost_returnsAzureOpenAI_whenBaseUrlMatchesAzure() {
		OpenAiOfficialSetup.ModelHost result = OpenAiOfficialSetup.detectModelHost(false, false,
				"https://example.openai.azure.com", null, null);

		assertEquals(OpenAiOfficialSetup.ModelHost.AZURE_OPENAI, result);
	}

	@Test
	void detectModelHost_returnsGitHubModels_whenBaseUrlMatchesGitHub() {
		OpenAiOfficialSetup.ModelHost result = OpenAiOfficialSetup.detectModelHost(false, false,
				"https://models.inference.ai.azure.com", null, null);

		assertEquals(OpenAiOfficialSetup.ModelHost.GITHUB_MODELS, result);
	}

	@Test
	void detectModelHost_returnsOpenAI_whenNoConditionsMatch() {
		OpenAiOfficialSetup.ModelHost result = OpenAiOfficialSetup.detectModelHost(false, false, null, null, null);

		assertEquals(OpenAiOfficialSetup.ModelHost.OPENAI, result);
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
		String result = OpenAiOfficialSetup.calculateBaseUrl(null, OpenAiOfficialSetup.ModelHost.OPENAI, null, null,
				null);

		assertEquals(OpenAiOfficialSetup.OPENAI_URL, result);
	}

	@Test
	void calculateBaseUrl_returnsGitHubUrl_whenModelHostIsGitHub() {
		String result = OpenAiOfficialSetup.calculateBaseUrl(null, OpenAiOfficialSetup.ModelHost.GITHUB_MODELS, null,
				null, null);

		assertEquals(OpenAiOfficialSetup.GITHUB_MODELS_URL, result);
	}

}

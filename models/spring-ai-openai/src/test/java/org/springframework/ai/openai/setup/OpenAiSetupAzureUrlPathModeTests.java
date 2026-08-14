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

import java.time.Duration;
import java.util.List;

import com.openai.azure.AzureOpenAIServiceVersion;
import com.openai.client.OpenAIClient;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.embeddings.EmbeddingCreateParams;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates the Microsoft Foundry URL path issue from #6060: when the SDK keeps
 * {@code AzureUrlPathMode.AUTO}, a proxy host fails the Azure-domain host whitelist and
 * the SDK skips Azure URL shaping, so requests miss
 * {@code /openai/deployments/{deployment}} and the {@code api-version} query and return
 * 404. With the path mode propagated from the {@code microsoft-foundry} provider
 * decision, the SDK produces the expected Azure-shaped request regardless of the host,
 * and the unified {@code /openai/v1} endpoint still produces the plain OpenAI-shaped
 * path.
 *
 * @author Jewoo Shin
 */
public class OpenAiSetupAzureUrlPathModeTests {

	private static final String CHAT_COMPLETION_RESPONSE = "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion\","
			+ "\"created\":1700000000,\"model\":\"gpt-4\",\"choices\":[{\"index\":0,"
			+ "\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":\"stop\"}],"
			+ "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":1,\"total_tokens\":6}}";

	private static final String EMBEDDING_RESPONSE = "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\","
			+ "\"embedding\":[0.1,0.2,0.3],\"index\":0}],\"model\":\"gpt-4\","
			+ "\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";

	@Test
	void microsoftFoundryProxyHostProducesAzureDeploymentPathForEmbeddings() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(EMBEDDING_RESPONSE));
			server.start();

			// Proxy host: the MockWebServer URL has no Azure domain suffix, so the SDK's
			// AzureUrlPathMode.AUTO would classify it as NON_AZURE. Forcing LEGACY via
			// the Foundry provider decision is what makes the deployment path appear.
			String proxyBaseUrl = stripTrailingSlash(server.url("/").toString());

			OpenAIClient client = OpenAiSetup.setupSyncClient(proxyBaseUrl, "test-api-key", null, "gpt-4",
					AzureOpenAIServiceVersion.latestStableVersion(), null, true, false, "gpt-4", Duration.ofSeconds(10),
					0, null, null, ObservationRegistry.NOOP, null, List.of());

			client.embeddings().create(EmbeddingCreateParams.builder().model("gpt-4").input("Hello").build());

			RecordedRequest request = server.takeRequest();
			assertThat(request.getPath())
				.as("Foundry proxy host must route embeddings through an Azure deployment path")
				.startsWith("/openai/deployments/gpt-4/embeddings");
			assertThat(request.getPath()).as("Foundry LEGACY mode must append the api-version query")
				.contains("api-version=");
		}
	}

	@Test
	void microsoftFoundryUnifiedEndpointProducesPlainOpenAiPath() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(CHAT_COMPLETION_RESPONSE));
			server.start();

			// Unified endpoint: when the base URL already ends with /openai/v1, the SDK
			// should keep the plain OpenAI-shaped path and not inject a deployment.
			String unifiedBaseUrl = stripTrailingSlash(server.url("/openai/v1").toString());

			OpenAIClient client = OpenAiSetup.setupSyncClient(unifiedBaseUrl, "test-api-key", null, null, null, null,
					true, false, "gpt-4", Duration.ofSeconds(10), 0, null, null, ObservationRegistry.NOOP, null,
					List.of());

			client.chat()
				.completions()
				.create(ChatCompletionCreateParams.builder().model(ChatModel.GPT_4).addUserMessage("Hi").build());

			RecordedRequest request = server.takeRequest();
			assertThat(request.getPath())
				.as("Foundry UNIFIED mode must keep the plain OpenAI-shaped path under /openai/v1")
				.startsWith("/openai/v1/chat/completions")
				.doesNotContain("/deployments/");
		}
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

}

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

package org.springframework.ai.google.genai.embedding;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiEmbeddingConnectionDetails}.
 *
 * @author Subhash Polisetti
 */
class GoogleGenAiEmbeddingConnectionDetailsTests {

	private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

	@Test
	void vertexClientIsBuiltFromSuppliedCredentials() throws Exception {
		GoogleGenAiEmbeddingConnectionDetails connectionDetails = GoogleGenAiEmbeddingConnectionDetails.builder()
			.projectId("test-project")
			.location("us-central1")
			.credentials(scopedCredentials())
			.build();

		Client client = connectionDetails.getGenAiClient();
		assertThat(client.vertexAI()).isTrue();
		assertThat(client.project()).isEqualTo("test-project");
		assertThat(client.location()).isEqualTo("us-central1");
	}

	@Test
	void apiKeyModeIgnoresCredentials() throws Exception {
		GoogleGenAiEmbeddingConnectionDetails connectionDetails = GoogleGenAiEmbeddingConnectionDetails.builder()
			.apiKey("test-api-key")
			.credentials(scopedCredentials())
			.build();

		assertThat(connectionDetails.getGenAiClient().vertexAI()).isFalse();
	}

	private static GoogleCredentials scopedCredentials() throws Exception {
		String json = "{" + "\"type\": \"service_account\"," + "\"project_id\": \"test-project\","
				+ "\"private_key_id\": \"test-key-id\"," + "\"private_key\": \"" + generatePrivateKeyPem() + "\","
				+ "\"client_email\": \"test@test-project.iam.gserviceaccount.com\","
				+ "\"client_id\": \"123456789012345678901\"," + "\"token_uri\": \"https://oauth2.googleapis.com/token\""
				+ "}";
		return GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))
			.createScoped(List.of(CLOUD_PLATFORM_SCOPE));
	}

	private static String generatePrivateKeyPem() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair keyPair = generator.generateKeyPair();
		String base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
		return "-----BEGIN PRIVATE KEY-----\\n" + base64 + "\\n-----END PRIVATE KEY-----\\n";
	}

}

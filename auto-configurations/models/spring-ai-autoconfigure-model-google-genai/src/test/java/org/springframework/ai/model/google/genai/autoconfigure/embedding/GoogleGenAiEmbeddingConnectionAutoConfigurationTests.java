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

package org.springframework.ai.model.google.genai.autoconfigure.embedding;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GoogleGenAiEmbeddingConnectionAutoConfiguration}.
 *
 * @author Subhash Polisetti
 */
class GoogleGenAiEmbeddingConnectionAutoConfigurationTests {

	private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

	@Test
	void credentialsLoadedFromUriAreScopedToCloudPlatform() throws Exception {
		GoogleCredentials credentials = GoogleGenAiEmbeddingConnectionAutoConfiguration
			.loadScopedCredentials(serviceAccountResource());

		assertThat(credentials).isInstanceOf(ServiceAccountCredentials.class);
		assertThat(((ServiceAccountCredentials) credentials).getScopes()).containsExactly(CLOUD_PLATFORM_SCOPE);
	}

	@Test
	void credentialsUriStreamIsClosed() throws Exception {
		AtomicBoolean closed = new AtomicBoolean();
		Resource credentialsUri = trackingCloseResource(serviceAccountJson(), closed);

		GoogleGenAiEmbeddingConnectionAutoConfiguration.loadScopedCredentials(credentialsUri);

		assertThat(closed).isTrue();
	}

	private static Resource serviceAccountResource() throws Exception {
		return new ByteArrayResource(serviceAccountJson());
	}

	private static Resource trackingCloseResource(byte[] content, AtomicBoolean closed) {
		return new ByteArrayResource(content) {
			@Override
			public InputStream getInputStream() {
				return new FilterInputStream(new ByteArrayInputStream(content)) {
					@Override
					public void close() throws IOException {
						closed.set(true);
						super.close();
					}
				};
			}
		};
	}

	private static byte[] serviceAccountJson() throws Exception {
		String json = "{" + "\"type\": \"service_account\"," + "\"project_id\": \"test-project\","
				+ "\"private_key_id\": \"test-key-id\"," + "\"private_key\": \"" + generatePrivateKeyPem() + "\","
				+ "\"client_email\": \"test@test-project.iam.gserviceaccount.com\","
				+ "\"client_id\": \"123456789012345678901\"," + "\"token_uri\": \"https://oauth2.googleapis.com/token\""
				+ "}";
		return json.getBytes(StandardCharsets.UTF_8);
	}

	private static String generatePrivateKeyPem() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair keyPair = generator.generateKeyPair();
		String base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
		return "-----BEGIN PRIVATE KEY-----\\n" + base64 + "\\n-----END PRIVATE KEY-----\\n";
	}

}

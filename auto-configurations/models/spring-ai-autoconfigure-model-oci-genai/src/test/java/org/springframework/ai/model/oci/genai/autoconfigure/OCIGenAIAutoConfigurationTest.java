/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.oci.genai.autoconfigure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;

import com.oracle.bmc.http.client.pki.Pem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.oci.cohere.OCICohereChatModel;
import org.springframework.ai.oci.cohere.OCICohereChatOptions;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OCIGenAIAutoConfigurationTest {

	@Test
	void setProperties(@TempDir Path tempDir) throws Exception {
		Path tmp = tempDir.resolve("my-key.pem");
		createPrivateKey(tmp);
		ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
		// @formatter:off
				"spring.ai.oci.genai.authenticationType=simple",
				"spring.ai.oci.genai.userId=my-user",
				"spring.ai.oci.genai.tenantId=my-tenant",
				"spring.ai.oci.genai.fingerprint=xyz",
				"spring.ai.oci.genai.privateKey=" + tmp.toAbsolutePath(),
				"spring.ai.oci.genai.region=us-ashburn-1",
				"spring.ai.oci.genai.cohere.chat.options.compartment=my-compartment",
				"spring.ai.oci.genai.cohere.chat.options.servingMode=dedicated",
				"spring.ai.oci.genai.cohere.chat.options.model=my-model",
				"spring.ai.oci.genai.cohere.chat.options.maxTokens=1000",
				"spring.ai.oci.genai.cohere.chat.options.temperature=0.5",
				"spring.ai.oci.genai.cohere.chat.options.topP=0.8",
				"spring.ai.oci.genai.cohere.chat.options.maxTokens=1000",
				"spring.ai.oci.genai.cohere.chat.options.frequencyPenalty=0.1",
				"spring.ai.oci.genai.cohere.chat.options.presencePenalty=0.2",

				"spring.ai.oci.genai.connect-timeout:1s",
				"spring.ai.oci.genai.read-timeout:1s",
				"spring.ai.oci.genai.max-async-threads:30"
				// @formatter:on
		).withConfiguration(SpringAiTestAutoConfigurations.of(OCIGenAiChatAutoConfiguration.class));

		contextRunner.run(context -> {
			OCICohereChatModel chatModel = context.getBean(OCICohereChatModel.class);
			assertThat(chatModel).isNotNull();
			OCICohereChatOptions options = (OCICohereChatOptions) chatModel.getDefaultOptions();
			assertThat(options.getCompartment()).isEqualTo("my-compartment");
			assertThat(options.getModel()).isEqualTo("my-model");
			assertThat(options.getServingMode()).isEqualTo("dedicated");
			assertThat(options.getMaxTokens()).isEqualTo(1000);
			assertThat(options.getTemperature()).isEqualTo(0.5);
			assertThat(options.getTopP()).isEqualTo(0.8);
			assertThat(options.getFrequencyPenalty()).isEqualTo(0.1);
			assertThat(options.getPresencePenalty()).isEqualTo(0.2);

			OCIConnectionProperties props = context.getBean(OCIConnectionProperties.class);
			assertThat(props.getAuthenticationType()).isEqualTo(OCIConnectionProperties.AuthenticationType.SIMPLE);
			assertThat(props.getUserId()).isEqualTo("my-user");
			assertThat(props.getTenantId()).isEqualTo("my-tenant");
			assertThat(props.getFingerprint()).isEqualTo("xyz");
			assertThat(props.getPrivateKey()).isEqualTo(tmp.toAbsolutePath().toString());
			assertThat(props.getRegion()).isEqualTo("us-ashburn-1");

			assertThat(props.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
			assertThat(props.getReadTimeout()).isEqualTo(Duration.ofSeconds(1));
			assertThat(props.getMaxAsyncThreads()).isEqualTo(30);

		});
	}

	private void createPrivateKey(Path tmp) throws Exception {
		KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
		gen.initialize(2048);
		KeyPair keyPair = gen.generateKeyPair();
		byte[] encoded = Pem.encoder().encode(keyPair.getPrivate());
		Files.write(tmp, encoded);
	}

}

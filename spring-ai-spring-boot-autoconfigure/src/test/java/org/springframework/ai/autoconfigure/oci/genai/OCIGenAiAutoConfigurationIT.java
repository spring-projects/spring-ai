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

package org.springframework.ai.autoconfigure.oci.genai;

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.oci.OCIEmbeddingModel;
import org.springframework.ai.oci.cohere.OCICohereChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = OCIGenAiAutoConfigurationIT.COMPARTMENT_ID_KEY, matches = ".+")
public class OCIGenAiAutoConfigurationIT {

	public static final String COMPARTMENT_ID_KEY = "OCI_COMPARTMENT_ID";

	public static final String OCI_CHAT_MODEL_ID_KEY = "OCI_CHAT_MODEL_ID";

	private final String CONFIG_FILE = Paths.get(System.getProperty("user.home"), ".oci", "config").toString();

	private final String COMPARTMENT_ID = System.getenv(COMPARTMENT_ID_KEY);

	private final String CHAT_MODEL_ID = System.getenv(OCI_CHAT_MODEL_ID_KEY);

	private final ApplicationContextRunner embeddingContextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
				"spring.ai.oci.genai.authenticationType=file",
				"spring.ai.oci.genai.file=" + this.CONFIG_FILE,
				"spring.ai.oci.genai.embedding.compartment=" + this.COMPARTMENT_ID,
				"spring.ai.oci.genai.embedding.servingMode=on-demand",
				"spring.ai.oci.genai.embedding.model=cohere.embed-english-light-v2.0"
				// @formatter:on
	).withConfiguration(AutoConfigurations.of(OCIGenAiAutoConfiguration.class));

	private final ApplicationContextRunner cohereChatContextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
			"spring.ai.oci.genai.authenticationType=file",
			"spring.ai.oci.genai.file=" + CONFIG_FILE,
			"spring.ai.oci.genai.cohere.chat.options.compartment=" + COMPARTMENT_ID,
			"spring.ai.oci.genai.cohere.chat.options.servingMode=on-demand",
			"spring.ai.oci.genai.cohere.chat.options.model=" + CHAT_MODEL_ID
			// @formatter:on
	).withConfiguration(AutoConfigurations.of(OCIGenAiAutoConfiguration.class));

	@Test
	void embeddings() {
		this.embeddingContextRunner.run(context -> {
			OCIEmbeddingModel embeddingModel = context.getBean(OCIEmbeddingModel.class);
			assertThat(embeddingModel).isNotNull();
			EmbeddingResponse response = embeddingModel
				.call(new EmbeddingRequest(List.of("There are 50 states in the USA", "Canada has 10 provinces"), null));
			assertThat(response).isNotNull();
			assertThat(response.getResults()).hasSize(2);
		});
	}

	@Test
	@EnabledIfEnvironmentVariable(named = OCIGenAiAutoConfigurationIT.OCI_CHAT_MODEL_ID_KEY, matches = ".+")
	void cohereChat() {
		this.cohereChatContextRunner.run(context -> {
			OCICohereChatModel chatModel = context.getBean(OCICohereChatModel.class);
			assertThat(chatModel).isNotNull();
			String response = chatModel.call("How many states are in the United States of America?");
			assertThat(response).isNotBlank();
		});
	}

}

/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.oci;

import java.io.IOException;
import java.nio.file.Paths;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;

public class BaseEmbeddingModelTest {

	public static final String OCI_COMPARTMENT_ID_KEY = "OCI_COMPARTMENT_ID";

	public static final String EMBEDDING_MODEL_V2 = "cohere.embed-english-light-v2.0";

	public static final String EMBEDDING_MODEL_V3 = "cohere.embed-english-v3.0";

	private static final String CONFIG_FILE = Paths.get(System.getProperty("user.home"), ".oci", "config").toString();

	private static final String PROFILE = "DEFAULT";

	private static final String REGION = "us-chicago-1";

	private static final String COMPARTMENT_ID = System.getenv(OCI_COMPARTMENT_ID_KEY);

	/**
	 * Create an OCIEmbeddingModel instance using a config file authentication provider.
	 * @return OCIEmbeddingModel instance
	 */
	public static OCIEmbeddingModel get() {
		try {
			ConfigFileAuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(
					CONFIG_FILE, PROFILE);
			GenerativeAiInferenceClient aiClient = GenerativeAiInferenceClient.builder()
				.region(Region.valueOf(REGION))
				.build(authProvider);
			OCIEmbeddingOptions options = OCIEmbeddingOptions.builder()
				.withModel(EMBEDDING_MODEL_V2)
				.withCompartment(COMPARTMENT_ID)
				.withServingMode("on-demand")
				.build();
			return new OCIEmbeddingModel(aiClient, options);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}

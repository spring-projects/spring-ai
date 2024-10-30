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
import com.oracle.bmc.generativeaiinference.GenerativeAiInference;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import org.springframework.ai.oci.cohere.OCICohereChatOptions;

public class BaseOCIGenAITest {

	public static final String OCI_COMPARTMENT_ID_KEY = "OCI_COMPARTMENT_ID";

	public static final String OCI_CHAT_MODEL_ID_KEY = "OCI_CHAT_MODEL_ID";

	public static final String CONFIG_FILE = Paths.get(System.getProperty("user.home"), ".oci", "config").toString();

	public static final String PROFILE = "DEFAULT";

	public static final String REGION = "us-chicago-1";

	public static final String COMPARTMENT_ID = System.getenv(OCI_COMPARTMENT_ID_KEY);

	public static final String CHAT_MODEL_ID = System.getenv(OCI_CHAT_MODEL_ID_KEY);

	public static GenerativeAiInference getGenerativeAIClient() {
		try {
			ConfigFileAuthenticationDetailsProvider authProvider = new ConfigFileAuthenticationDetailsProvider(
					CONFIG_FILE, PROFILE);
			return GenerativeAiInferenceClient.builder().region(Region.valueOf(REGION)).build(authProvider);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static OCICohereChatOptions.Builder options() {
		return OCICohereChatOptions.builder()
			.withModel(CHAT_MODEL_ID)
			.withCompartment(COMPARTMENT_ID)
			.withServingMode("on-demand");
	}

}

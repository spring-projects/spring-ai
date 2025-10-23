/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.openaiofficial.setup;

import com.azure.identity.AuthenticationUtil;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;

/**
 * Helps configure the OpenAI Java SDK, depending on the platform used. This code is
 * inspired by LangChain4j's
 * `dev.langchain4j.model.openaiofficial.AzureInternalOpenAiOfficialHelper` class, which
 * is coded by the same author (Julien Dubois, from Microsoft).
 *
 * @author Julien Dubois
 */
class AzureInternalOpenAiOfficialHelper {

	static Credential getAzureCredential() {
		return BearerTokenCredential.create(AuthenticationUtil.getBearerTokenSupplier(
				new DefaultAzureCredentialBuilder().build(), "https://cognitiveservices.azure.com/.default"));
	}

}

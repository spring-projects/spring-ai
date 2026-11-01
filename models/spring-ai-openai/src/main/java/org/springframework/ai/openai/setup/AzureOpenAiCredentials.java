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

import com.azure.core.credential.TokenCredential;
import com.openai.credential.Credential;

import org.springframework.util.Assert;

/**
 * Factory for adapting Azure credentials for use with OpenAI models hosted on Microsoft
 * Foundry.
 *
 * @author c.kai
 * @since 2.0.2
 */
public final class AzureOpenAiCredentials {

	private AzureOpenAiCredentials() {
	}

	/**
	 * Adapt an Azure {@link TokenCredential} to an OpenAI {@link Credential}.
	 * @param tokenCredential the Azure credential to adapt
	 * @return an OpenAI credential configured for Microsoft Foundry
	 */
	public static Credential from(TokenCredential tokenCredential) {
		Assert.notNull(tokenCredential, "tokenCredential must not be null");
		return AzureInternalOpenAiHelper.getAzureCredential(tokenCredential);
	}

}

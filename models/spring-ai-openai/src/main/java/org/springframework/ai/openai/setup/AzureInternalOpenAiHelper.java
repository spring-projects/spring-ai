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

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;
import org.jspecify.annotations.Nullable;

/**
 * Specific configuration for authenticating on Azure. This is in a separate class to
 * avoid needing the Azure SDK dependencies when not using Azure as a platform.
 *
 * This code is inspired by LangChain4j's
 * `dev.langchain4j.model.openaiofficial.AzureInternalOpenAiOfficialHelper` class, which
 * is coded by the same author (Julien Dubois, from Microsoft).
 *
 * @author Julien Dubois
 */
final class AzureInternalOpenAiHelper {

	private static final String COGNITIVE_SERVICES_SCOPE = "https://cognitiveservices.azure.com/.default";

	private AzureInternalOpenAiHelper() {
	}

	static Credential getAzureCredential() {
		return getAzureCredential(new DefaultAzureCredentialBuilder().build());
	}

	static Credential getAzureCredential(TokenCredential credential) {
		TokenRequestContext request = new TokenRequestContext().addScopes(COGNITIVE_SERVICES_SCOPE);
		// Not via AuthenticationUtil.getBearerTokenSupplier: that sends a
		// request to https://www.example.com on every call to harvest the
		// Authorization header, which fails behind TLS-inspecting proxies.
		return BearerTokenCredential.create(new CachingTokenSupplier(credential, request));
	}

	/**
	 * Caches the token because {@link BearerTokenCredential} calls this supplier on each
	 * request, so the credential is queried only on first use and when it nears expiry.
	 */
	private static final class CachingTokenSupplier implements Supplier<String> {

		private final TokenCredential credential;

		private final TokenRequestContext request;

		private @Nullable AccessToken cachedToken;

		CachingTokenSupplier(TokenCredential credential, TokenRequestContext request) {
			this.credential = credential;
			this.request = request;
		}

		@Override
		public synchronized String get() {
			@Nullable AccessToken token = this.cachedToken;
			if (token == null || OffsetDateTime.now().plusMinutes(5).isAfter(token.getExpiresAt())) {
				token = this.credential.getTokenSync(this.request);
				this.cachedToken = token;
			}
			return token.getToken();
		}

	}

}

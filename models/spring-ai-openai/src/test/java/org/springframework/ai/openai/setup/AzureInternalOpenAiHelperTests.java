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

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.openai.credential.BearerTokenCredential;
import com.openai.credential.Credential;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link AzureInternalOpenAiHelper}.
 *
 * @author Subhash Polisetti
 */
class AzureInternalOpenAiHelperTests {

	private static final String COGNITIVE_SERVICES_SCOPE = "https://cognitiveservices.azure.com/.default";

	private final TokenCredential credential = mock(TokenCredential.class);

	@Test
	void getAzureCredentialResolvesTokenDirectlyFromCredential() {
		givenTokens(new AccessToken("test-token", OffsetDateTime.now().plusHours(1)));

		Credential azureCredential = AzureInternalOpenAiHelper.getAzureCredential(this.credential);

		assertThat(azureCredential).isInstanceOf(BearerTokenCredential.class);
		assertThat(((BearerTokenCredential) azureCredential).token()).isEqualTo("test-token");
	}

	@Test
	void getAzureCredentialRequestsCognitiveServicesScope() {
		givenTokens(new AccessToken("test-token", OffsetDateTime.now().plusHours(1)));

		bearerTokenCredential().token();

		ArgumentCaptor<TokenRequestContext> captor = ArgumentCaptor.forClass(TokenRequestContext.class);
		verify(this.credential).getTokenSync(captor.capture());
		assertThat(captor.getValue().getScopes()).containsExactly(COGNITIVE_SERVICES_SCOPE);
	}

	@Test
	void getAzureCredentialDoesNotAcquireTokenEagerly() {
		AzureInternalOpenAiHelper.getAzureCredential(this.credential);

		verifyNoInteractions(this.credential);
	}

	@Test
	void getAzureCredentialCachesTokenAcrossCalls() {
		givenTokens(new AccessToken("test-token", OffsetDateTime.now().plusHours(1)));

		BearerTokenCredential azureCredential = bearerTokenCredential();
		assertThat(azureCredential.token()).isEqualTo("test-token");
		assertThat(azureCredential.token()).isEqualTo("test-token");
		assertThat(azureCredential.token()).isEqualTo("test-token");

		verify(this.credential, times(1)).getTokenSync(any(TokenRequestContext.class));
	}

	@Test
	void getAzureCredentialRefreshesTokenNearExpiry() {
		// The first token expires within the refresh margin, so it is not reused.
		givenTokens(new AccessToken("first", OffsetDateTime.now().plusMinutes(1)),
				new AccessToken("second", OffsetDateTime.now().plusHours(1)));

		BearerTokenCredential azureCredential = bearerTokenCredential();

		assertThat(azureCredential.token()).isEqualTo("first");
		assertThat(azureCredential.token()).isEqualTo("second");
		assertThat(azureCredential.token()).isEqualTo("second");
		verify(this.credential, times(2)).getTokenSync(any(TokenRequestContext.class));
	}

	private void givenTokens(AccessToken token, AccessToken... additionalTokens) {
		given(this.credential.getTokenSync(any(TokenRequestContext.class))).willReturn(token, additionalTokens);
	}

	private BearerTokenCredential bearerTokenCredential() {
		return (BearerTokenCredential) AzureInternalOpenAiHelper.getAzureCredential(this.credential);
	}

}

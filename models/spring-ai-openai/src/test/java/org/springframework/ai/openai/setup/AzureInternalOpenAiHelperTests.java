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

class AzureInternalOpenAiHelperTests {

	@Test
	void getAzureCredentialResolvesTokenDirectlyFromCredential() {
		AccessToken accessToken = new AccessToken("test-token", OffsetDateTime.now().plusHours(1));
		TokenCredential credential = mock(TokenCredential.class);
		given(credential.getTokenSync(any(TokenRequestContext.class))).willReturn(accessToken);

		Credential azureCredential = AzureInternalOpenAiHelper.getAzureCredential(credential);

		assertThat(azureCredential).isInstanceOf(BearerTokenCredential.class);
		assertThat(((BearerTokenCredential) azureCredential).token()).isEqualTo("test-token");
	}

	@Test
	void getAzureCredentialRequestsCognitiveServicesScope() {
		AccessToken accessToken = new AccessToken("test-token", OffsetDateTime.now().plusHours(1));
		TokenCredential credential = mock(TokenCredential.class);
		given(credential.getTokenSync(any(TokenRequestContext.class))).willReturn(accessToken);

		((BearerTokenCredential) AzureInternalOpenAiHelper.getAzureCredential(credential)).token();

		ArgumentCaptor<TokenRequestContext> captor = ArgumentCaptor.forClass(TokenRequestContext.class);
		verify(credential).getTokenSync(captor.capture());
		assertThat(captor.getValue().getScopes()).containsExactly("https://cognitiveservices.azure.com/.default");
	}

	@Test
	void getAzureCredentialCachesTokenAcrossCalls() {
		AccessToken accessToken = new AccessToken("test-token", OffsetDateTime.now().plusHours(1));
		TokenCredential credential = mock(TokenCredential.class);
		given(credential.getTokenSync(any(TokenRequestContext.class))).willReturn(accessToken);

		BearerTokenCredential azureCredential = (BearerTokenCredential) AzureInternalOpenAiHelper
			.getAzureCredential(credential);
		azureCredential.token();
		azureCredential.token();
		azureCredential.token();

		verify(credential, times(1)).getTokenSync(any(TokenRequestContext.class));
	}

	@Test
	void getAzureCredentialRefreshesTokenNearExpiry() {
		TokenCredential credential = mock(TokenCredential.class);
		given(credential.getTokenSync(any(TokenRequestContext.class))).willReturn(
				new AccessToken("first", OffsetDateTime.now().plusMinutes(1)),
				new AccessToken("second", OffsetDateTime.now().plusHours(1)));

		BearerTokenCredential azureCredential = (BearerTokenCredential) AzureInternalOpenAiHelper
			.getAzureCredential(credential);

		assertThat(azureCredential.token()).isEqualTo("first");
		assertThat(azureCredential.token()).isEqualTo("second");
		verify(credential, times(2)).getTokenSync(any(TokenRequestContext.class));
	}

}

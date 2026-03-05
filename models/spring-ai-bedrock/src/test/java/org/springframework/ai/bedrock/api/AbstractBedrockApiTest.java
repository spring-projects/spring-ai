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

package org.springframework.ai.bedrock.api;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractBedrockApiTest {

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	private DefaultAwsRegionProviderChain.Builder awsRegionProviderBuilder;

	@Mock
	private AwsCredentialsProvider awsCredentialsProvider = mock(AwsCredentialsProvider.class);

	@Mock
	private JsonMapper jsonMapper = mock(JsonMapper.class);

	@Test
	void shouldLoadRegionFromAwsDefaults() {
		try (MockedStatic<DefaultAwsRegionProviderChain> mocked = mockStatic(DefaultAwsRegionProviderChain.class)) {
			when(this.awsRegionProviderBuilder.build().getRegion()).thenReturn(Region.AF_SOUTH_1);
			mocked.when(DefaultAwsRegionProviderChain::builder).thenReturn(this.awsRegionProviderBuilder);
			AbstractBedrockApi<Object, Object, Object> testBedrockApi = new TestBedrockApi("modelId",
					this.awsCredentialsProvider, null, this.jsonMapper, Duration.ofMinutes(5));
			assertThat(testBedrockApi.getRegion()).isEqualTo(Region.AF_SOUTH_1);
		}
	}

	@Test
	void shouldThrowIllegalArgumentIfAwsDefaultsFailed() {
		try (MockedStatic<DefaultAwsRegionProviderChain> mocked = mockStatic(DefaultAwsRegionProviderChain.class)) {
			when(this.awsRegionProviderBuilder.build().getRegion())
				.thenThrow(SdkClientException.builder().message("failed load").build());
			mocked.when(DefaultAwsRegionProviderChain::builder).thenReturn(this.awsRegionProviderBuilder);
			assertThatThrownBy(() -> new TestBedrockApi("modelId", this.awsCredentialsProvider, null, this.jsonMapper,
					Duration.ofMinutes(5)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("failed load");
		}
	}

	private static class TestBedrockApi extends AbstractBedrockApi<Object, Object, Object> {

		protected TestBedrockApi(String modelId, AwsCredentialsProvider credentialsProvider, Region region,
				JsonMapper jsonMapper, Duration timeout) {
			super(modelId, credentialsProvider, region, jsonMapper, timeout);
		}

		@Override
		protected Object embedding(Object request) {
			return null;
		}

		@Override
		protected Object chatCompletion(Object request) {
			return null;
		}

		@Override
		protected Object internalInvocation(Object request, Class<Object> clazz) {
			return null;
		}

	}

}

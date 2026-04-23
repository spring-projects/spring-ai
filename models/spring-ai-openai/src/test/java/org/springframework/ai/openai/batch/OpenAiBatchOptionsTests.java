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

package org.springframework.ai.openai.batch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenAiBatchOptions}.
 *
 * @author Yasin Akbas
 */
class OpenAiBatchOptionsTests {

	@Test
	void shouldUseDefaults() {
		OpenAiBatchOptions options = OpenAiBatchOptions.builder().build();

		assertThat(options.getCompletionWindow()).isEqualTo("24h");
		assertThat(options.getMaxRequestsPerBatch()).isEqualTo(50_000);
		assertThat(options.getMaxFileSizeBytes()).isEqualTo(200L * 1024 * 1024);
		assertThat(options.getTokenSafetyFactor()).isEqualTo(1.2);
		assertThat(options.getMinimumTokensToSubmit()).isEqualTo(5_000_000L);
		assertThat(options.getMaxRetryAttempts()).isEqualTo(2);
		assertThat(options.isDeleteFilesAfterProcessing()).isTrue();
	}

	@Test
	void shouldApplyCustomValues() {
		OpenAiBatchOptions options = OpenAiBatchOptions.builder()
			.completionWindow("48h")
			.maxRequestsPerBatch(10_000)
			.maxFileSizeBytes(100L * 1024 * 1024)
			.tokenSafetyFactor(1.5)
			.minimumTokensToSubmit(1_000_000L)
			.maxRetryAttempts(5)
			.deleteFilesAfterProcessing(false)
			.build();

		assertThat(options.getCompletionWindow()).isEqualTo("48h");
		assertThat(options.getMaxRequestsPerBatch()).isEqualTo(10_000);
		assertThat(options.getMaxFileSizeBytes()).isEqualTo(100L * 1024 * 1024);
		assertThat(options.getTokenSafetyFactor()).isEqualTo(1.5);
		assertThat(options.getMinimumTokensToSubmit()).isEqualTo(1_000_000L);
		assertThat(options.getMaxRetryAttempts()).isEqualTo(5);
		assertThat(options.isDeleteFilesAfterProcessing()).isFalse();
	}

	@Test
	void shouldCopyOptions() {
		OpenAiBatchOptions original = OpenAiBatchOptions.builder()
			.completionWindow("12h")
			.maxRequestsPerBatch(1000)
			.baseUrl("https://custom.api.com")
			.apiKey("sk-test")
			.build();

		OpenAiBatchOptions copy = original.copy();

		assertThat(copy.getCompletionWindow()).isEqualTo("12h");
		assertThat(copy.getMaxRequestsPerBatch()).isEqualTo(1000);
		assertThat(copy.getBaseUrl()).isEqualTo("https://custom.api.com");
		assertThat(copy.getApiKey()).isEqualTo("sk-test");
		assertThat(copy).isNotSameAs(original);
	}

	@Test
	void shouldSetConnectionProperties() {
		OpenAiBatchOptions options = OpenAiBatchOptions.builder()
			.baseUrl("https://api.openai.com")
			.apiKey("sk-test-key")
			.organizationId("org-123")
			.build();

		assertThat(options.getBaseUrl()).isEqualTo("https://api.openai.com");
		assertThat(options.getApiKey()).isEqualTo("sk-test-key");
		assertThat(options.getOrganizationId()).isEqualTo("org-123");
	}

	@Test
	void shouldBuildFromExistingOptions() {
		OpenAiBatchOptions original = OpenAiBatchOptions.builder()
			.completionWindow("6h")
			.maxRequestsPerBatch(500)
			.tokenSafetyFactor(1.8)
			.apiKey("original-key")
			.build();

		OpenAiBatchOptions rebuilt = OpenAiBatchOptions.builder().from(original).maxRequestsPerBatch(1000).build();

		assertThat(rebuilt.getCompletionWindow()).isEqualTo("6h");
		assertThat(rebuilt.getMaxRequestsPerBatch()).isEqualTo(1000);
		assertThat(rebuilt.getTokenSafetyFactor()).isEqualTo(1.8);
		assertThat(rebuilt.getApiKey()).isEqualTo("original-key");
	}

	@Test
	void shouldImplementEqualsAndHashCode() {
		OpenAiBatchOptions options1 = OpenAiBatchOptions.builder()
			.completionWindow("24h")
			.maxRequestsPerBatch(1000)
			.build();

		OpenAiBatchOptions options2 = OpenAiBatchOptions.builder()
			.completionWindow("24h")
			.maxRequestsPerBatch(1000)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

}

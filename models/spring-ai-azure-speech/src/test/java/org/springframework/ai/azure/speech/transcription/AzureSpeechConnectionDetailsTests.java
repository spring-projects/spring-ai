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

package org.springframework.ai.azure.speech.transcription;

import java.net.URI;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link AzureSpeechConnectionDetails}.
 *
 * @author Spring AI Contributors
 */
class AzureSpeechConnectionDetailsTests {

	@Test
	void acceptsAKeyWithARegion() {
		AzureSpeechConnectionDetails details = AzureSpeechConnectionDetails.builder()
			.apiKey("secret-key")
			.region("westus")
			.build();

		assertThat(details.getApiKey()).isEqualTo("secret-key");
		assertThat(details.getRegion()).isEqualTo("westus");
		assertThat(details.getEndpoint()).isNull();
	}

	@Test
	void acceptsAKeyWithAnEndpoint() {
		AzureSpeechConnectionDetails details = AzureSpeechConnectionDetails.builder()
			.apiKey("secret-key")
			.endpoint("https://westus.api.cognitive.microsoft.com")
			.build();

		assertThat(details.getEndpoint()).isEqualTo(URI.create("https://westus.api.cognitive.microsoft.com"));
		assertThat(details.getRegion()).isNull();
	}

	@Test
	void namesTheMissingPropertyWithoutRepeatingAnyValue() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechConnectionDetails.builder().region("westus").build())
			.withMessageContaining("spring.ai.azure.speech.api-key")
			.withMessageNotContaining("westus");
	}

	@Test
	void requiresExactlyOneOfRegionAndEndpoint() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechConnectionDetails.builder().apiKey("k").build())
			.withMessageContaining("neither was set");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechConnectionDetails.builder()
				.apiKey("k")
				.region("westus")
				.endpoint("https://example.invalid")
				.build())
			.withMessageContaining("both were set");
	}

	@Test
	void rejectsAnUnparseableEndpointWithoutEchoingIt() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechConnectionDetails.builder()
				.apiKey("k")
				.endpoint("https://example.invalid/ not a uri")
				.build())
			.withMessageContaining("is not a valid URI")
			.withMessageNotContaining("not a uri");
	}

	@Test
	void toStringRedactsTheKeyAndOmitsTheEndpoint() {
		String rendered = AzureSpeechConnectionDetails.builder()
			.apiKey("super-secret")
			.endpoint("https://westus.api.cognitive.microsoft.com?key=super-secret")
			.build()
			.toString();

		assertThat(rendered).doesNotContain("super-secret").contains("[REDACTED]").contains("endpoint=[SET]");
	}

}

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
import java.net.URISyntaxException;

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

/**
 * Connection settings for the key-based Azure Speech authentication the model supports
 * out of the box: an API key with either a region or a service endpoint.
 * <p>
 * Anything else — Microsoft Entra ID, authorization tokens, private links, sovereign
 * clouds, or container hosts — is expressed by supplying your own
 * {@link AzureSpeechConfigFactory} instead of these details.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public final class AzureSpeechConnectionDetails {

	private final String apiKey;

	private final @Nullable String region;

	private final @Nullable URI endpoint;

	private AzureSpeechConnectionDetails(@Nullable String apiKey, @Nullable String region, @Nullable String endpoint) {
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException(
					"Azure Speech requires an API key (property: spring.ai.azure.speech.api-key)");
		}
		boolean hasRegion = StringUtils.hasText(region);
		boolean hasEndpoint = StringUtils.hasText(endpoint);
		if (hasRegion == hasEndpoint) {
			throw new IllegalArgumentException("Azure Speech requires exactly one of a region "
					+ "(property: spring.ai.azure.speech.region) or an endpoint "
					+ "(property: spring.ai.azure.speech.endpoint)"
					+ (hasRegion ? ", but both were set" : ", but neither was set"));
		}
		this.apiKey = apiKey;
		this.region = hasRegion ? region : null;
		this.endpoint = (endpoint != null && hasEndpoint) ? toUri(endpoint) : null;
	}

	private static URI toUri(String endpoint) {
		try {
			return new URI(endpoint);
		}
		catch (URISyntaxException ex) {
			// The endpoint may carry a credential, so neither it nor the parse failure's
			// message is repeated here.
			throw new IllegalArgumentException(
					"Azure Speech endpoint (property: spring.ai.azure.speech.endpoint) is not a valid URI");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getApiKey() {
		return this.apiKey;
	}

	public @Nullable String getRegion() {
		return this.region;
	}

	public @Nullable URI getEndpoint() {
		return this.endpoint;
	}

	/**
	 * Redacts the API key. The endpoint is omitted too, because it can carry a credential
	 * in its query string.
	 */
	@Override
	public String toString() {
		return "AzureSpeechConnectionDetails{apiKey=[REDACTED], region=" + this.region + ", endpoint="
				+ ((this.endpoint != null) ? "[SET]" : "null") + '}';
	}

	public static final class Builder {

		private @Nullable String apiKey;

		private @Nullable String region;

		private @Nullable String endpoint;

		private Builder() {
		}

		public Builder apiKey(@Nullable String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public Builder region(@Nullable String region) {
			this.region = region;
			return this;
		}

		public Builder endpoint(@Nullable String endpoint) {
			this.endpoint = endpoint;
			return this;
		}

		public AzureSpeechConnectionDetails build() {
			return new AzureSpeechConnectionDetails(this.apiKey, this.region, this.endpoint);
		}

	}

}

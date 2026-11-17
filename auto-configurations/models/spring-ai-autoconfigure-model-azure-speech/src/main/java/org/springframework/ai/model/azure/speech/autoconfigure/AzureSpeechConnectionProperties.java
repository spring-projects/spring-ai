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

package org.springframework.ai.model.azure.speech.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.azure.speech.transcription.AzureSpeechConfigFactory;
import org.springframework.ai.azure.speech.transcription.AzureSpeechConnectionDetails;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection properties for the key-based Azure AI Speech authentication that is
 * supported out of the box: an API key with either a region or a service endpoint.
 * <p>
 * These properties are read only when the application does not define its own
 * {@link AzureSpeechConfigFactory} bean. Every other authentication method — Microsoft
 * Entra ID, authorization tokens, private links, sovereign clouds, or container hosts —
 * is expressed by supplying that bean instead, in which case none of these properties is
 * required or validated.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
@ConfigurationProperties(AzureSpeechConnectionProperties.CONFIG_PREFIX)
public class AzureSpeechConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.azure.speech";

	/**
	 * Azure AI Speech resource key.
	 */
	private @Nullable String apiKey;

	/**
	 * Azure region of the Speech resource, such as {@code westeurope}. Exactly one of the
	 * region or the endpoint must be set.
	 */
	private @Nullable String region;

	/**
	 * Azure AI Speech service endpoint. Required for custom endpoints, private links, and
	 * continuous language identification. Exactly one of the region or the endpoint must
	 * be set.
	 */
	private @Nullable String endpoint;

	public @Nullable String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(@Nullable String apiKey) {
		this.apiKey = apiKey;
	}

	public @Nullable String getRegion() {
		return this.region;
	}

	public void setRegion(@Nullable String region) {
		this.region = region;
	}

	public @Nullable String getEndpoint() {
		return this.endpoint;
	}

	public void setEndpoint(@Nullable String endpoint) {
		this.endpoint = endpoint;
	}

	/**
	 * Converts these properties into validated connection details.
	 * @return the connection details backing the default configuration factory
	 * @throws IllegalArgumentException when the key is missing, when neither a region nor
	 * an endpoint is set, or when both are set. The message names the property at fault
	 * and never repeats a configured value.
	 */
	public AzureSpeechConnectionDetails toConnectionDetails() {
		return AzureSpeechConnectionDetails.builder()
			.apiKey(this.apiKey)
			.region(this.region)
			.endpoint(this.endpoint)
			.build();
	}

	/**
	 * Redacts the API key. The endpoint is omitted too, because it can carry a credential
	 * in its query string.
	 */
	@Override
	public String toString() {
		return "AzureSpeechConnectionProperties{apiKey=" + ((this.apiKey != null) ? "[REDACTED]" : "null") + ", region="
				+ this.region + ", endpoint=" + ((this.endpoint != null) ? "[SET]" : "null") + '}';
	}

}

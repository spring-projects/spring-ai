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

import com.microsoft.cognitiveservices.speech.SpeechConfig;

import org.springframework.util.Assert;

/**
 * Creates a {@link SpeechConfig} from key-based {@link AzureSpeechConnectionDetails}:
 * {@code fromEndpoint} when an endpoint is configured, otherwise {@code fromSubscription}
 * with the region.
 * <p>
 * A new configuration is created on every call, as {@link AzureSpeechConfigFactory}
 * requires, which also makes token refresh straightforward for factories that extend this
 * behavior.
 *
 * @author Spring AI Contributors
 * @since 2.1.0
 */
public class DefaultAzureSpeechConfigFactory implements AzureSpeechConfigFactory {

	private final AzureSpeechConnectionDetails connectionDetails;

	public DefaultAzureSpeechConfigFactory(AzureSpeechConnectionDetails connectionDetails) {
		Assert.notNull(connectionDetails, "connectionDetails must not be null");
		this.connectionDetails = connectionDetails;
	}

	@Override
	public SpeechConfig create() {
		URI endpoint = this.connectionDetails.getEndpoint();
		if (endpoint != null) {
			return SpeechConfig.fromEndpoint(endpoint, this.connectionDetails.getApiKey());
		}
		String region = this.connectionDetails.getRegion();
		Assert.state(region != null, "connectionDetails must carry a region when no endpoint is set");
		return SpeechConfig.fromSubscription(this.connectionDetails.getApiKey(), region);
	}

	/**
	 * Never includes the API key or the endpoint.
	 */
	@Override
	public String toString() {
		return "DefaultAzureSpeechConfigFactory{connectionDetails=" + this.connectionDetails + '}';
	}

}

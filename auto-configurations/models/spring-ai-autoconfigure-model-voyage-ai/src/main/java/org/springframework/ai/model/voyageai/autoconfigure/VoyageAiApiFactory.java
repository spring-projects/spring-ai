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

package org.springframework.ai.model.voyageai.autoconfigure;

import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.ai.voyageai.api.VoyageAiResponseErrorHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Creates the {@link VoyageAiApi} that the Voyage AI auto-configurations share the
 * settings of.
 *
 * @author Zoltan Fodi
 */
final class VoyageAiApiFactory {

	private VoyageAiApiFactory() {
	}

	static VoyageAiApi create(VoyageAiConnectionProperties properties,
			ObjectProvider<RestClient.Builder> restClientBuilder,
			ObjectProvider<ResponseErrorHandler> responseErrorHandler) {
		String apiKey = properties.getApiKey();
		Assert.hasText(apiKey, "The Voyage AI API key must be set: spring.ai.voyageai.api-key");
		return VoyageAiApi.builder()
			.baseUrl(properties.getBaseUrl())
			.apiKey(apiKey)
			.restClientBuilder(restClientBuilder.getIfAvailable(RestClient::builder))
			// the handler of the retry auto-configuration decides which errors are
			// retried
			.responseErrorHandler(new VoyageAiResponseErrorHandler(responseErrorHandler.getIfAvailable()))
			.build();
	}

}

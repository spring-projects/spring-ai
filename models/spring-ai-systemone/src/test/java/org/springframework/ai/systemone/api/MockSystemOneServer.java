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

package org.springframework.ai.systemone.api;

import org.springframework.ai.systemone.api.exception.SystemOneApiException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

/**
 * Test fixture pairing a {@link MockRestServiceServer} with a {@link SystemOneClient}
 * that speaks to it. The mock server has to be bound to the {@link RestClient.Builder}
 * before the client is built, because the client clones that builder.
 *
 * @author Christian Tzolov
 */
final class MockSystemOneServer {

	static final String BASE_URL = "https://api.typesafe.ai";

	static final String SYSTEM_ONE_URL = BASE_URL + "/v1/systemone";

	static final String MODELS_URL = BASE_URL + "/v1/models";

	private final MockRestServiceServer server;

	private final RestClient.Builder restClientBuilder;

	private MockSystemOneServer(RestClient.Builder restClientBuilder, MockRestServiceServer server) {
		this.restClientBuilder = restClientBuilder;
		this.server = server;
	}

	static MockSystemOneServer create() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).bufferContent().build();
		return new MockSystemOneServer(builder, server);
	}

	MockRestServiceServer server() {
		return this.server;
	}

	RestClient.Builder restClientBuilder() {
		return this.restClientBuilder;
	}

	SystemOneClient client() {
		return clientBuilder().build();
	}

	SystemOneClient client(RetryTemplate retryTemplate) {
		return clientBuilder().retryTemplate(retryTemplate).build();
	}

	SystemOneClient.Builder clientBuilder() {
		return SystemOneClient.builder()
			.apiKey("test-api-key")
			.baseUrl(BASE_URL)
			.defaultModel(SystemOneModel.LATEST.id())
			.restClientBuilder(this.restClientBuilder);
	}

	/**
	 * @return a response creator for a successful JSON body carrying a request id
	 */
	static org.springframework.test.web.client.ResponseCreator jsonResponse(String body) {
		return MockRestResponseCreators.withSuccess(body, MediaType.APPLICATION_JSON)
			.header(SystemOneApiException.REQUEST_ID_HEADER, "req_0123456789");
	}

	/**
	 * @return a response creator for an error status with an arbitrary body and headers
	 */
	static org.springframework.test.web.client.ResponseCreator errorResponse(int status, String body,
			HttpHeaders headers) {
		return MockRestResponseCreators.withStatus(HttpStatusCode.valueOf(status))
			.contentType(MediaType.APPLICATION_JSON)
			.headers(headers)
			.body(body);
	}

	static org.springframework.test.web.client.ResponseCreator errorResponse(int status, String body) {
		return errorResponse(status, body, new HttpHeaders());
	}

}

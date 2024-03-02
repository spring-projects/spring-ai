/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.openai.api.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * @author Christian Tzolov
 */
public class ApiUtils {

	public static Consumer<HttpHeaders> getJsonContentHeaders(String apiKey) {
		return (headers) -> {
			headers.setBearerAuth(apiKey);
			headers.setContentType(MediaType.APPLICATION_JSON);
		};
	};

	public static final ResponseErrorHandler DEFAULT_RESPONSE_ERROR_HANDLER = new ResponseErrorHandler() {

		@Override
		public boolean hasError(@NonNull ClientHttpResponse response) throws IOException {
			return response.getStatusCode().isError();
		}

		@Override
		public void handleError(@NonNull ClientHttpResponse response) throws IOException {
			if (response.getStatusCode().isError()) {
				String error = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
				String message = String.format("%s - %s", response.getStatusCode().value(), error);
				if (response.getStatusCode().is4xxClientError()) {
					throw new OpenAiApiClientErrorException(message);
				}
				throw new OpenAiApiException(message);
			}
		}
	};

}

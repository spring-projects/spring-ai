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

package org.springframework.ai.mistralai.api;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * {@link ClientHttpRequestInterceptor} to apply a content length header based on the body
 * length if this header was not present in the request headers.
 *
 * @author Nicolas Krier
 */
public class ContentLengthInterceptor implements ClientHttpRequestInterceptor {

	// @formatter:off
	// Temporary solution to fix the following error:
	// org.springframework.ai.retry.NonTransientAiException: 411 - {
	//		"message":"A valid Content-Length header is required",
	//		"request_id":"5108031f4a1e0d3e6d66204d56b2ac60"
	// }
	// TODO: Discuss with Sébastien Deleuze the opportunity to add this class into Spring Framework if this solution is satisfying.
	// @formatter:on
	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		var headers = request.getHeaders();

		if (!headers.containsHeader(HttpHeaders.CONTENT_LENGTH)) {
			headers.setContentLength(body.length);
		}

		return execution.execute(request, body);
	}

}

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

package org.springframework.ai.voyageai;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * A {@link VoyageAiApi} that talks to a mock server.
 */
final class MockVoyageAi {

	final MockRestServiceServer server;

	final VoyageAiApi api;

	MockVoyageAi() {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.api = VoyageAiApi.builder().apiKey("test-key").restClientBuilder(builder).build();
	}

	ResponseActions expectPost(String path) {
		return this.server.expect(requestTo("https://api.voyageai.com" + path))
			.andExpect(method(org.springframework.http.HttpMethod.POST));
	}

	/**
	 * Answer with {@code count} embeddings of one component each: {@code base + index}.
	 * The embeddings are listed in reverse order, as nothing promises the order.
	 */
	static org.springframework.test.web.client.response.DefaultResponseCreator embeddings(int count, int base,
			String usage) {
		String data = IntStream.range(0, count)
			.map(i -> count - 1 - i)
			.mapToObj(i -> "{\"object\":\"embedding\",\"embedding\":[" + (base + i) + ".0],\"index\":" + i + "}")
			.collect(Collectors.joining(","));
		return withSuccess("{\"object\":\"list\",\"data\":[" + data + "],\"model\":\"voyage-4\"" + usage + "}",
				MediaType.APPLICATION_JSON);
	}

	static final String USAGE = ",\"usage\":{\"total_tokens\":10}";

}

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

package org.springframework.ai.model.openai.autoconfigure;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenAiAzureChatHttpTests {

	@Test
	public void isAzureOpenAiEndpointRecognizesAzureHosts() {
		assertThat(OpenAIAutoConfigurationUtil.isAzureOpenAiEndpoint("https://my-resource.openai.azure.com")).isTrue();
		assertThat(OpenAIAutoConfigurationUtil
			.isAzureOpenAiEndpoint("https://my-resource.openai.azure.com/openai/deployments/x")).isTrue();
		assertThat(OpenAIAutoConfigurationUtil.isAzureOpenAiEndpoint("  https://Ab.openai.azure.com  ")).isTrue();
		assertThat(OpenAIAutoConfigurationUtil.isAzureOpenAiEndpoint("https://openai.azure.com")).isTrue();
	}

	@Test
	public void isAzureOpenAiEndpointRejectsNonAzureHosts() {
		assertThat(OpenAIAutoConfigurationUtil.isAzureOpenAiEndpoint("https://api.openai.com")).isFalse();
		assertThat(OpenAIAutoConfigurationUtil.isAzureOpenAiEndpoint("https://localhost:8080")).isFalse();
		assertThat(OpenAIAutoConfigurationUtil.isAzureOpenAiEndpoint("")).isFalse();
		assertThat(OpenAIAutoConfigurationUtil.isAzureOpenAiEndpoint(null)).isFalse();
		assertThat(OpenAIAutoConfigurationUtil.isAzureOpenAiEndpoint("not-a-uri")).isFalse();
	}

	@Test
	public void prepareRestClientBuilderForOpenAiSendsContentLengthOnPostToLocalServer() throws Exception {
		AtomicReference<String> contentLength = new AtomicReference<>();
		AtomicReference<String> transferEncoding = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/v1/chat/completions", exchange -> {
			contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
			transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
			String response = """
					{"id":"1","object":"chat.completion","created":0,"model":"gpt","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
					""";
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		server.start();
		int port = server.getAddress().getPort();
		try {
			RestClient client = OpenAIAutoConfigurationUtil
				.prepareRestClientBuilderForOpenAi(RestClient.builder(), "https://resource.openai.azure.com")
				.baseUrl("http://127.0.0.1:" + port)
				.build();
			String body = client.post()
				.uri("/v1/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
				.retrieve()
				.body(String.class);
			assertThat(body).contains("chat.completion");
			assertThat(contentLength.get()).isNotNull().isNotBlank();
			assertThat(transferEncoding.get()).isNull();
		}
		finally {
			server.stop(0);
		}
	}

}

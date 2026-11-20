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

package org.springframework.ai.google.genai;

import java.time.Duration;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests that {@link GoogleGenAiChatModel#stream(Prompt)} closes the underlying response
 * body of the SDK {@link com.google.genai.ResponseStream} when the subscription is
 * cancelled after receiving an element.
 *
 * @author Yu Xia
 */
class GoogleGenAiStreamCancellationTests {

	@Test
	void closesResponseBodyWhenCancelled() {
		String frame = """
				data: {"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]}}],"modelVersion":"gemini-test"}

				""";

		ResponseBody body = spy(ResponseBody.create(frame.repeat(3), MediaType.get("text/event-stream")));

		OkHttpClient httpClient = new OkHttpClient.Builder()
			.addInterceptor(chain -> new okhttp3.Response.Builder().request(chain.request())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.header("Content-Type", "text/event-stream")
				.body(body)
				.build())
			.build();

		try (Client client = Client.builder()
			.apiKey("test-key")
			.vertexAI(false)
			.clientOptions(ClientOptions.builder().customHttpClient(httpClient).build())
			.build()) {

			GoogleGenAiChatModel model = GoogleGenAiChatModel.builder().genAiClient(client).build();

			ChatResponse response = model.stream(new Prompt("Hello")).next().block(Duration.ofSeconds(5));

			assertThat(response).isNotNull();
			assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello");

			verify(body).close();
		}
	}

}

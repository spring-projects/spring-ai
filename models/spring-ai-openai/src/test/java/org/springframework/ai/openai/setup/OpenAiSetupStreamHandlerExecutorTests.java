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

package org.springframework.ai.openai.setup;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import javax.management.JMException;
import javax.management.ObjectName;

import com.openai.client.OpenAIClientAsync;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the stream handler executor of the clients built by {@link OpenAiSetup}.
 *
 * @author Kris Young
 */
class OpenAiSetupStreamHandlerExecutorTests {

	private static final String RETAINED_CLASS = "com.openai.core.PhantomReachableExecutorService";

	private static final String BASE_URL = "http://localhost:1/v1";

	private static final int CLIENT_PAIRS = 100;

	private static final String STREAM_BODY = """
			data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":1700000000,"model":"gpt-4","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}

			data: [DONE]

			""";

	@Test
	void shouldDeliverStreamChunksOnStreamHandlerThread() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "text/event-stream")
				.setBody(STREAM_BODY));
			server.start();
			OpenAIClientAsync client = OpenAiSetup.setupAsyncClient(server.url("/v1").toString(), "test-key", null,
					null, null, null, false, false, "gpt-4", Duration.ofSeconds(10), 0, null, null,
					ObservationRegistry.NOOP, null, List.of());
			Queue<String> threadNames = new ConcurrentLinkedQueue<>();

			client.chat()
				.completions()
				.createStreaming(
						ChatCompletionCreateParams.builder().model(ChatModel.GPT_4).addUserMessage("Hi").build())
				.subscribe(chunk -> threadNames.add(Thread.currentThread().getName()))
				.onCompleteFuture()
				.get(10, TimeUnit.SECONDS);

			assertThat(threadNames).singleElement().asString().startsWith("openai-stream-handler-thread-");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "test-key", "" })
	void shouldNotRetainStreamExecutorsOfDiscardedClients(String apiKey) throws Exception {
		assertThat(Class.forName(RETAINED_CLASS)).isNotNull();
		long before = liveInstances();

		for (int i = 0; i < CLIENT_PAIRS; i++) {
			OpenAiSetup.setupSyncClient(BASE_URL, apiKey, null, null, null, null, false, false, "gpt-4",
					Duration.ofSeconds(10), 0, null, null, ObservationRegistry.NOOP, null, List.of());
			OpenAiSetup.setupAsyncClient(BASE_URL, apiKey, null, null, null, null, false, false, "gpt-4",
					Duration.ofSeconds(10), 0, null, null, ObservationRegistry.NOOP, null, List.of());
		}

		assertThat(liveInstances() - before).isZero();
	}

	private static long liveInstances() throws JMException {
		String histogram = (String) ManagementFactory.getPlatformMBeanServer()
			.invoke(new ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
					new Object[] { null }, new String[] { String[].class.getName() });
		return Arrays.stream(histogram.split("\n"))
			.map(line -> line.trim().split("\\s+"))
			.filter(columns -> columns.length > 3 && RETAINED_CLASS.equals(columns[3]))
			.mapToLong(columns -> Long.parseLong(columns[1]))
			.sum();
	}

}

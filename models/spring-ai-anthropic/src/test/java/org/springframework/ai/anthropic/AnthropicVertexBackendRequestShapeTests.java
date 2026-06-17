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

package org.springframework.ai.anthropic;

import java.util.List;

import com.anthropic.backends.AnthropicBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the generic SDK {@code Backend} overloads in {@link AnthropicSetup} and
 * {@link AnthropicChatModel.Builder#backend} work end-to-end.
 *
 * @author dragonfsky
 */
class AnthropicVertexBackendRequestShapeTests {

	@Test
	void syncClientCreatedWithAnthropicBackend() {
		AnthropicBackend backend = AnthropicBackend.builder().apiKey("test-key").build();

		AnthropicClient client = AnthropicSetup.setupSyncClient(backend, null, null, null, null,
				ObservationRegistry.NOOP, null, null, List.of());

		assertThat(client).isNotNull();
	}

	@Test
	void asyncClientCreatedWithAnthropicBackend() {
		AnthropicBackend backend = AnthropicBackend.builder().apiKey("test-key").build();

		AnthropicClientAsync client = AnthropicSetup.setupAsyncClient(backend, null, null, null, null,
				ObservationRegistry.NOOP, null, null, List.of());

		assertThat(client).isNotNull();
	}

	@Test
	void chatModelBuilderAcceptsBackend() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().model("claude-sonnet-4-5").maxTokens(100).build();
		AnthropicBackend backend = AnthropicBackend.builder().apiKey("test-key").build();

		AnthropicChatModel model = AnthropicChatModel.builder().options(options).backend(backend).build();

		assertThat(model).isNotNull();
		assertThat(model.getOptions().getModel()).isEqualTo("claude-sonnet-4-5");
	}

}

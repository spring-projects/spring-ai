/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.zhipuai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ZhiPuAiChatOptions}.
 *
 * @author Alexandros Pappas
 */
class ZhiPuAiChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		ZhiPuAiChatOptions options = ZhiPuAiChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stop(List.of("test"))
			.temperature(0.6)
			.topP(0.7)
			.toolChoice("auto")
			.user("test-user")
			.requestId("12345")
			.doSample(true)
			.proxyToolCalls(true)
			.toolContext(Map.of("key1", "value1"))
			.build();

		assertThat(options)
			.extracting("model", "maxTokens", "stop", "temperature", "topP", "toolChoice", "user", "requestId",
					"doSample", "proxyToolCalls", "toolContext")
			.containsExactly("test-model", 100, List.of("test"), 0.6, 0.7, "auto", "test-user", "12345", true, true,
					Map.of("key1", "value1"));
	}

	@Test
	void testCopy() {
		ZhiPuAiChatOptions original = ZhiPuAiChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stop(List.of("test"))
			.temperature(0.6)
			.topP(0.7)
			.toolChoice("auto")
			.user("test-user")
			.requestId("12345")
			.doSample(true)
			.proxyToolCalls(true)
			.toolContext(Map.of("key1", "value1"))
			.build();

		ZhiPuAiChatOptions copied = original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		assertThat(copied.getStop()).isNotSameAs(original.getStop());
		assertThat(copied.getFunctionCallbacks()).isNotSameAs(original.getFunctionCallbacks());
		assertThat(copied.getFunctions()).isNotSameAs(original.getFunctions());
		assertThat(copied.getToolContext()).isNotSameAs(original.getToolContext());
	}

	@Test
	void testSetters() {
		ZhiPuAiChatOptions options = new ZhiPuAiChatOptions();
		options.setModel("test-model");
		options.setMaxTokens(100);
		options.setStop(List.of("test"));
		options.setTemperature(0.6);
		options.setTopP(0.7);
		options.setToolChoice("auto");
		options.setUser("test-user");
		options.setRequestId("12345");
		options.setDoSample(true);
		options.setProxyToolCalls(true);
		options.setToolContext(Map.of("key1", "value1"));

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getStop()).isEqualTo(List.of("test"));
		assertThat(options.getTemperature()).isEqualTo(0.6);
		assertThat(options.getTopP()).isEqualTo(0.7);
		assertThat(options.getToolChoice()).isEqualTo("auto");
		assertThat(options.getUser()).isEqualTo("test-user");
		assertThat(options.getRequestId()).isEqualTo("12345");
		assertThat(options.getDoSample()).isTrue();
		assertThat(options.getProxyToolCalls()).isTrue();
		assertThat(options.getToolContext()).isEqualTo(Map.of("key1", "value1"));
	}

	@Test
	void testDefaultValues() {
		ZhiPuAiChatOptions options = new ZhiPuAiChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getTools()).isNull();
		assertThat(options.getToolChoice()).isNull();
		assertThat(options.getUser()).isNull();
		assertThat(options.getRequestId()).isNull();
		assertThat(options.getDoSample()).isNull();
		assertThat(options.getFunctionCallbacks()).isEqualTo(new ArrayList<>());
		assertThat(options.getFunctions()).isEqualTo(new HashSet<>());
		assertThat(options.getProxyToolCalls()).isNull();
		assertThat(options.getToolContext()).isEqualTo(new HashMap<>());
	}

}

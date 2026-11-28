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

package org.springframework.ai.chat.client.advisor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolInputException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolInputRecoveryTests {

	static Stream<Arguments> recoveryCases() {
		return Stream.of("primitive", "primitiveNull", "null", "date", "json", "valid", "body", "conversion", "throw")
			.flatMap(kind -> Stream.of(false, true)
				.flatMap(direct -> Stream.of(false, true).map(streaming -> Arguments.of(kind, direct, streaming))));
	}

	@ParameterizedTest
	@MethodSource("recoveryCases")
	void inputFailuresCanRecoverWithoutChangingOtherFailures(String kind, boolean direct, boolean streaming)
			throws Exception {
		var tools = new SampleTools();
		boolean numeric = kind.equals("primitive") || kind.equals("primitiveNull") || kind.equals("body");
		var method = SampleTools.class.getMethod(kind.equals("body") ? "fails" : numeric ? "count" : "date",
				numeric ? int.class : LocalDateTime.class);
		String valid = numeric ? "{\"count\":1}" : "{\"when\":\"2026-10-29T09:00:00\"}";
		String invalid = switch (kind) {
			case "primitive" -> "{}";
			case "primitiveNull" -> "{\"count\":null}";
			case "null" -> "null";
			case "date", "throw" -> "{\"when\":\"tomorrow\"}";
			case "json" -> "{";
			default -> valid;
		};
		var callbackBuilder = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.builder(method).name("sample").description("Test tool").build())
			.toolMetadata(ToolMetadata.builder().returnDirect(direct).build())
			.toolObject(tools)
			.toolMethod(method);
		if (kind.equals("conversion")) {
			callbackBuilder.toolCallResultConverter((result, type) -> {
				throw new IllegalStateException("conversion failed");
			});
		}
		var callback = callbackBuilder.build();
		var processed = new AtomicInteger();
		var processor = new DefaultToolExecutionExceptionProcessor(kind.equals("throw"));
		var manager = DefaultToolCallingManager.builder().toolExecutionExceptionProcessor(error -> {
			processed.incrementAndGet();
			assertThat(error instanceof ToolInputException).isEqualTo(!kind.equals("body"));
			return processor.process(error);
		}).build();
		var model = new ScriptedModel(invalid, valid);
		var client = ChatClient.builder(model)
			.defaultAdvisors(ToolCallingAdvisor.builder().toolCallingManager(manager).build())
			.build();
		Supplier<String> invoke = () -> {
			var request = client.prompt().user("Use sample").toolCallbacks(callback);
			return streaming ? request.stream().content().collectList().map(parts -> String.join("", parts)).block()
					: request.call().content();
		};
		if (kind.equals("throw")) {
			assertThatThrownBy(invoke::get).isInstanceOf(ToolExecutionException.class);
			assertThat(model.calls).isEqualTo(1);
			assertThat(tools.executions).isZero();
			assertThat(processed).hasValue(1);
		}
		else if (kind.equals("conversion")) {
			assertThatThrownBy(invoke::get).isInstanceOf(IllegalStateException.class).hasMessage("conversion failed");
			assertThat(model.calls).isEqualTo(1);
			assertThat(tools.executions).isEqualTo(1);
			assertThat(processed).hasValue(0);
		}
		else {
			String result = invoke.get();
			boolean inputFailure = !kind.equals("valid") && !kind.equals("body");
			assertThat(model.calls).isEqualTo(direct ? inputFailure ? 2 : 1 : 3);
			assertThat(tools.executions).isEqualTo(inputFailure ? 1 : direct ? 1 : 2);
			assertThat(processed).hasValue(inputFailure ? 1 : kind.equals("body") ? direct ? 1 : 2 : 0);
			if (!direct) {
				assertThat(result).isEqualTo("done");
			}
			else if (kind.equals("body")) {
				assertThat(result).isEqualTo("body failed");
			}
			else {
				assertThat(result).isEqualTo(numeric ? "1" : "\"2026-10-29T09:00\"");
			}
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void inputFailurePreventsDirectReturnForWholeBatch(boolean invalidFirst) throws Exception {
		var tools = new SampleTools();
		var method = SampleTools.class.getMethod("count", int.class);
		var callback = MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.builder(method).name("sample").description("Test tool").build())
			.toolMetadata(ToolMetadata.builder().returnDirect(true).build())
			.toolObject(tools)
			.toolMethod(method)
			.build();
		var calls = List.of(
				new AssistantMessage.ToolCall("first", "function", "sample", invalidFirst ? "{}" : "{\"count\":1}"),
				new AssistantMessage.ToolCall("second", "function", "sample", invalidFirst ? "{\"count\":1}" : "{}"));
		var response = new ChatResponse(
				List.of(new Generation(AssistantMessage.builder().content("").toolCalls(calls).build())));
		var options = ToolCallingChatOptions.builder().toolCallbacks(callback).build();
		var manager = DefaultToolCallingManager.builder().build();
		var result = manager.executeToolCalls(new Prompt("Use sample", options), response);
		assertThat(result.returnDirect()).isFalse();
		assertThat(tools.executions).isEqualTo(1);
		var messages = result.conversationHistory();
		var toolResponse = (ToolResponseMessage) messages.get(messages.size() - 1);
		assertThat(toolResponse.getResponses()).extracting(ToolResponseMessage.ToolResponse::id)
			.containsExactly("first", "second");
		assertThat(toolResponse.getResponses().get(invalidFirst ? 1 : 0).responseData()).isEqualTo("1");
	}

	public static class SampleTools {

		int executions;

		public String date(LocalDateTime when) {
			this.executions++;
			return when.toString();
		}

		public String count(int count) {
			this.executions++;
			return Integer.toString(count);
		}

		public String fails(int count) {
			this.executions++;
			throw new IllegalArgumentException("body failed");
		}

	}

	static class ScriptedModel implements ChatModel {

		private final String invalid;

		private final String valid;

		int calls;

		ScriptedModel(String invalid, String valid) {
			this.invalid = invalid;
			this.valid = valid;
		}

		@Override
		public ToolCallingChatOptions getOptions() {
			return ToolCallingChatOptions.builder().build();
		}

		@Override
		public ChatResponse call(Prompt prompt) {
			this.calls++;
			assertThat(this.calls).isLessThanOrEqualTo(3);
			if (this.calls > 1) {
				var response = (ToolResponseMessage) prompt.getInstructions().get(prompt.getInstructions().size() - 1);
				assertThat(response.getResponses()).hasSize(1);
				assertThat(response.getResponses().get(0).id()).isEqualTo("call-" + (this.calls - 1));
				assertThat(response.getResponses().get(0).responseData()).isNotBlank();
			}
			var message = this.calls <= 2 ? AssistantMessage.builder()
				.content("")
				.toolCalls(List.of(new AssistantMessage.ToolCall("call-" + this.calls, "function", "sample",
						this.calls == 1 ? this.invalid : this.valid)))
				.build() : new AssistantMessage("done");
			return new ChatResponse(List.of(new Generation(message)));
		}

		@Override
		public Flux<ChatResponse> stream(Prompt prompt) {
			return Flux.defer(() -> Flux.just(call(prompt)));
		}

	}

}

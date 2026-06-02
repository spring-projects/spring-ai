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

package org.springframework.ai.tool.toolsearch.advisor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.tool.toolsearch.index.lucene.LuceneToolIndex;
import org.springframework.ai.tool.toolsearch.index.regex.RegexToolIndex;
import org.springframework.ai.tool.toolsearch.index.vectorstore.VectorToolIndex;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Abstract base class for {@link ToolSearchToolCallingAdvisor} integration tests. Mirrors
 * the structure of {@code AbstractToolCallAdvisorIT} but exercises the full tool-search
 * flow: the LLM first calls {@code toolSearchTool} to discover tools, then invokes the
 * resolved tool.
 *
 * <p>
 * Each test is parameterized and runs against three {@link ToolIndex} implementations:
 * {@link RegexToolIndex}, {@link LuceneToolIndex}, and a {@link VectorToolIndex} backed
 * by a local ONNX embedding model ({@code all-MiniLM-L6-v2} via
 * {@link TransformersEmbeddingModel}).
 *
 * @author Christian Tzolov
 */
public abstract class AbstractToolSearchToolCallingAdvisorIT {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	protected abstract ChatModel getChatModel();

	/**
	 * Provides the three {@link ToolIndex} implementations used to parameterize every
	 * test. The {@link VectorToolIndex} variant uses a local ONNX embedding model, which
	 * is downloaded and cached on first run.
	 */
	static Stream<Arguments> toolIndexes() throws Exception {
		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		VectorToolIndex vectorToolIndex = new VectorToolIndex(SimpleVectorStore.builder(embeddingModel).build());

		return Stream.of(
				// PassThroughToolIndex returns every indexed tool for any query —
				// isolates the advisor lifecycle from search-quality variability.
				Arguments.of(Named.named("PassThroughToolIndex", new PassThroughToolIndex())),
				Arguments.of(Named.named("RegexToolIndex", new RegexToolIndex())),
				// Score threshold 0.0f: Lucene BM25 scores are not normalized and drop
				// very low in a 1-document corpus. IT tests exercise the advisor
				// lifecycle,
				// not search ranking, so any positive match is sufficient.
				Arguments.of(Named.named("LuceneToolIndex", new LuceneToolIndex(0.0f))),
				Arguments.of(Named.named("VectorToolIndex", vectorToolIndex)));
	}

	protected ToolSearchToolCallingAdvisor createToolSearchToolCallingAdvisor(ToolIndex toolIndex) {
		return ToolSearchToolCallingAdvisor.builder().toolIndex(toolIndex).build();
	}

	protected ToolSearchToolCallingAdvisor createToolSearchToolCallingAdvisorWithExternalMemory(ToolIndex toolIndex) {
		return ToolSearchToolCallingAdvisor.builder().toolIndex(toolIndex).disableInternalConversationHistory().build();
	}

	protected ToolCallback createWeatherToolCallback() {

		Method method = ReflectionUtils.findMethod(WeatherTool.class, "currentWeather", String.class);
		return MethodToolCallback.builder()
			.toolDefinition(ToolDefinitions.builder(method).description("Get the weather in location").build())
			.toolMethod(method)
			.toolObject(new WeatherTool())
			.build();
	}

	private static String join(Flux<String> flux) {
		return Objects.requireNonNull(flux.collectList().block()).stream().collect(Collectors.joining());
	}

	@Nested
	class CallTests {

		@ParameterizedTest(name = "[{index}] {0}")
		@MethodSource("org.springframework.ai.tool.toolsearch.advisor.AbstractToolSearchToolCallingAdvisorIT#toolIndexes")
		void callMultipleToolInvocations(ToolIndex toolIndex) {
			String response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(createToolSearchToolCallingAdvisor(toolIndex))
				.user("Use the weather tool to get the current temperature in San Francisco, Tokyo, and Paris. Report the exact readings in Celsius.")
				.toolCallbacks(createWeatherToolCallback())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "chat_memory_conversation_id"))
				.call()
				.content();

			logger.info("Response: {}", response);
			assertThat(response).contains("30", "10", "15");
		}

		@ParameterizedTest(name = "[{index}] {0}")
		@MethodSource("org.springframework.ai.tool.toolsearch.advisor.AbstractToolSearchToolCallingAdvisorIT#toolIndexes")
		void callMultipleToolInvocationsWithExternalMemory(ToolIndex toolIndex) {
			String response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(createToolSearchToolCallingAdvisorWithExternalMemory(toolIndex),
						MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build())
							.build())
				.user("Use the weather tool to get the current temperature in San Francisco, Tokyo, and Paris. Report the exact readings in Celsius.")
				.toolCallbacks(createWeatherToolCallback())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "chat_memory_conversation_id"))
				.call()
				.content();

			logger.info("Response: {}", response);
			assertThat(response).contains("30", "10", "15");
		}

		@ParameterizedTest(name = "[{index}] {0}")
		@MethodSource("org.springframework.ai.tool.toolsearch.advisor.AbstractToolSearchToolCallingAdvisorIT#toolIndexes")
		void callDefaultAdvisorConfiguration(ToolIndex toolIndex) {
			var chatClient = ChatClient.builder(getChatModel())
				.defaultAdvisors(createToolSearchToolCallingAdvisor(toolIndex))
				.build();

			String response = chatClient.prompt()
				.user("Use the weather tool to get the current temperature in San Francisco, Tokyo, and Paris. Report the exact readings in Celsius.")
				.toolCallbacks(createWeatherToolCallback())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "chat_memory_conversation_id"))
				.call()
				.content();

			logger.info("Response: {}", response);
			assertThat(response).contains("30", "10", "15");
		}

		@ParameterizedTest(name = "[{index}] {0}")
		@MethodSource("org.springframework.ai.tool.toolsearch.advisor.AbstractToolSearchToolCallingAdvisorIT#toolIndexes")
		void callDefaultAdvisorConfigurationWithExternalMemory(ToolIndex toolIndex) {
			var chatClient = ChatClient.builder(getChatModel())
				.defaultAdvisors(createToolSearchToolCallingAdvisorWithExternalMemory(toolIndex),
						MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
				.build();

			String response = chatClient.prompt()
				.user("Use the weather tool to get the current temperature in San Francisco, Tokyo, and Paris. Report the exact readings in Celsius.")
				.toolCallbacks(createWeatherToolCallback())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "chat_memory_conversation_id"))
				.call()
				.content();

			logger.info("Response: {}", response);
			assertThat(response).contains("30", "10", "15");
		}

	}

	@Nested
	class StreamTests {

		@ParameterizedTest(name = "[{index}] {0}")
		@MethodSource("org.springframework.ai.tool.toolsearch.advisor.AbstractToolSearchToolCallingAdvisorIT#toolIndexes")
		void streamMultipleToolInvocations(ToolIndex toolIndex) {
			Flux<String> response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(createToolSearchToolCallingAdvisor(toolIndex))
				.user("Use the weather tool to get the current temperature in San Francisco, Tokyo, and Paris. Report the exact readings in Celsius.")
				.toolCallbacks(createWeatherToolCallback())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "chat_memory_conversation_id"))
				.stream()
				.content();

			String content = join(response);
			logger.info("Response: {}", content);
			assertThat(content).contains("30", "10", "15");
		}

		@ParameterizedTest(name = "[{index}] {0}")
		@MethodSource("org.springframework.ai.tool.toolsearch.advisor.AbstractToolSearchToolCallingAdvisorIT#toolIndexes")
		void streamMultipleToolInvocationsWithExternalMemory(ToolIndex toolIndex) {
			Flux<String> response = ChatClient.create(getChatModel())
				.prompt()
				.advisors(createToolSearchToolCallingAdvisorWithExternalMemory(toolIndex),
						MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().maxMessages(500).build())
							.build())
				.user("Use the weather tool to get the current temperature in San Francisco, Tokyo, and Paris. Report the exact readings in Celsius.")
				.toolCallbacks(createWeatherToolCallback())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "chat_memory_conversation_id"))
				.stream()
				.content();

			String content = join(response);
			logger.info("Response: {}", content);
			assertThat(content).contains("30", "10", "15");
		}

		@ParameterizedTest(name = "[{index}] {0}")
		@MethodSource("org.springframework.ai.tool.toolsearch.advisor.AbstractToolSearchToolCallingAdvisorIT#toolIndexes")
		void streamDefaultAdvisorConfiguration(ToolIndex toolIndex) {
			var chatClient = ChatClient.builder(getChatModel())
				.defaultAdvisors(createToolSearchToolCallingAdvisor(toolIndex))
				.build();

			Flux<String> response = chatClient.prompt()
				.user("Use the weather tool to get the current temperature in San Francisco, Tokyo, and Paris. Report the exact readings in Celsius.")
				.toolCallbacks(createWeatherToolCallback())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "chat_memory_conversation_id"))
				.stream()
				.content();

			String content = join(response);
			logger.info("Response: {}", content);
			assertThat(content).contains("30", "10", "15");
		}

		@ParameterizedTest(name = "[{index}] {0}")
		@MethodSource("org.springframework.ai.tool.toolsearch.advisor.AbstractToolSearchToolCallingAdvisorIT#toolIndexes")
		void streamDefaultAdvisorConfigurationWithExternalMemory(ToolIndex toolIndex) {
			var chatClient = ChatClient.builder(getChatModel())
				.defaultAdvisors(createToolSearchToolCallingAdvisorWithExternalMemory(toolIndex),
						MessageChatMemoryAdvisor.builder(MessageWindowChatMemory.builder().build()).build())
				.build();

			Flux<String> response = chatClient.prompt()
				.user("Use the weather tool to get the current temperature in San Francisco, Tokyo, and Paris. Report the exact readings in Celsius.")
				.toolCallbacks(createWeatherToolCallback())
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "chat_memory_conversation_id"))
				.stream()
				.content();

			String content = join(response);
			logger.info("Response: {}", content);
			assertThat(content).contains("30", "10", "15");
		}

	}

	/**
	 * A ToolIndex that returns every indexed tool for any query. Suitable for integration
	 * tests where search quality is irrelevant — all that matters is that the advisor
	 * lifecycle (index → search → expand → invoke) works end-to-end.
	 */
	static class PassThroughToolIndex implements ToolIndex {

		private final ConcurrentHashMap<String, List<ToolReference>> sessionTools = new ConcurrentHashMap<>();

		@Override
		public void indexTool(String sessionId, ToolReference toolReference) {
			this.sessionTools.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(toolReference);
		}

		@Override
		public void clearIndex(String sessionId) {
			this.sessionTools.remove(sessionId);
		}

		@Override
		public ToolSearchResponse search(ToolSearchRequest req) {
			List<ToolReference> refs = this.sessionTools.getOrDefault(req.sessionId(), List.of());
			return ToolSearchResponse.builder()
				.toolReferences(refs)
				.totalMatches(refs.size())
				.searchMetadata(ToolSearchResponse.SearchMetadata.builder()
					.searchType("PassThroughToolIndex")
					.query(req.query())
					.build())
				.build();
		}

	}

	public static class WeatherTool {

		@Tool(description = "Get the current temperature in Celsius for a location or comma-separated list of locations")
		public String currentWeather(String location) {
			String loc = location.toLowerCase();
			StringBuilder result = new StringBuilder();
			if (loc.contains("san francisco") || loc.contains("francisco")) {
				result.append("San Francisco: 30°C");
			}
			if (loc.contains("tokyo")) {
				if (!result.isEmpty()) {
					result.append(", ");
				}
				result.append("Tokyo: 10°C");
			}
			if (loc.contains("paris")) {
				if (!result.isEmpty()) {
					result.append(", ");
				}
				result.append("Paris: 15°C");
			}
			// If the LLM passed an unrecognised location string, return all readings so
			// the test can still verify the end-to-end flow.
			return result.isEmpty() ? "San Francisco: 30°C, Tokyo: 10°C, Paris: 15°C" : result.toString();
		}

	}

}

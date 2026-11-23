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

package org.springframework.ai.deepseek.chat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.DeepSeekTestConfiguration;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.MockWeatherService;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.util.JsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DeepSeekChatModel}.
 *
 * @author Geng Rong
 * @author guan xu
 * @author Dimitar Proynov
 */
@SpringBootTest(classes = DeepSeekTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekChatModelIT {

	private static final JsonHelper jsonHelper = new JsonHelper();

	@Autowired
	protected ChatModel chatModel;

	@Autowired
	protected StreamingChatModel streamingChatModel;

	/**
	 * The bodies of the non-streaming requests of {@link #capturingChatModel}, to check
	 * what is sent back to DeepSeek.
	 */
	private final List<String> requestBodies = new CopyOnWriteArrayList<>();

	private DeepSeekChatModel capturingChatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@BeforeEach
	void setUpCapturingChatModel() {
		// Only the RestClient is intercepted, so only the bodies of call() are captured
		RestClient.Builder restClientBuilder = RestClient.builder().requestInterceptor((request, body, execution) -> {
			this.requestBodies.add(new String(body, StandardCharsets.UTF_8));
			return execution.execute(request, body);
		});
		DeepSeekApi deepSeekApi = DeepSeekApi.builder()
			.apiKey(Objects.requireNonNull(System.getenv("DEEPSEEK_API_KEY")))
			.restClientBuilder(restClientBuilder)
			.build();
		this.capturingChatModel = DeepSeekChatModel.builder().deepSeekApi(deepSeekApi).build();
	}

	@Test
	void roleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and what they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getText()).contains("Blackbeard");
		// needs fine tuning... evaluateQuestionAndAnswer(request, response, false);
	}

	@Test
	void listOutputConverter() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputConverter outputConverter = new ListOutputConverter(conversionService);

		String format = outputConverter.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("subject", "ice cream flavors", "format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		List<String> list = outputConverter.convert(generation.getOutput().getText());
		assertThat(list).hasSize(5);

	}

	@Test
	void mapOutputConverter() {
		MapOutputConverter outputConverter = new MapOutputConverter();

		String format = outputConverter.getFormat();
		String template = """
				   Please provide the JSON response without any code block markers such as ```json```.
				Provide me a List of {subject}
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format",
					format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		Map<String, Object> result = outputConverter.convert(generation.getOutput().getText());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	@Test
	void beanOutputConverter() {

		BeanOutputConverter<ActorsFilms> outputConverter = new BeanOutputConverter<>(ActorsFilms.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography for a random actor.
				Please provide the JSON response without any code block markers such as ```json```.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilms actorsFilms = outputConverter.convert(generation.getOutput().getText());
	}

	@Test
	void beanOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				Please provide the JSON response without any code block markers such as ```json```.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generation.getOutput().getText());
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				Please provide the JSON response without any code block markers such as ```json```.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		String generationTextFromStream = this.streamingChatModel.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(m -> m.getText() != null ? m.getText() : "")
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void prefixCompletionTest() {
		String userMessageContent = """
				Please return this yaml data to json.

				data:
				```yaml
				code: 200
				result:
				  total: 1
				  data:
				    - 1
				    - 2
				    - 3
				```
				""";
		UserMessage userMessage = new UserMessage(userMessageContent);
		Message assistantMessage = AssistantMessage.builder()
			.content("{\"code\":200,\"result\":{\"total\":1,\"data\":[1")
			.properties(Map.of(DeepSeekChatModel.PREFIX_METADATA_KEY, true))
			.build();
		Prompt prompt = new Prompt(List.of(userMessage, assistantMessage));
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).isEqualTo(",2,3]}}");
	}

	@Test
	void reasoningTest() {
		var promptOptions = DeepSeekChatOptions.builder().build();
		Prompt prompt = new Prompt("9.11 and 9.8, which is greater?", promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		AssistantMessage assistantMessage = response.getResult().getOutput();
		assertThat(assistantMessage.getReasoning()).isNotEmpty();
		assertThat(assistantMessage.getText()).isNotEmpty();
	}

	@Test
	void reasoningMultiRoundTest() {
		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("9.11 and 9.8, which is greater?"));
		var promptOptions = DeepSeekChatOptions.builder().build();

		Prompt prompt = new Prompt(messages, promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		AssistantMessage assistantMessage = response.getResult().getOutput();
		assertThat(assistantMessage.getReasoning()).isNotEmpty();
		assertThat(assistantMessage.getText()).isNotEmpty();

		// Replayed with its reasoning, which the API accepts and ignores without tools
		messages.add(assistantMessage);
		messages.add(new UserMessage("How many Rs are there in the word 'strawberry'?"));
		Prompt prompt2 = new Prompt(messages, promptOptions);
		ChatResponse response2 = this.chatModel.call(prompt2);

		AssistantMessage assistantMessage2 = response2.getResult().getOutput();
		assertThat(assistantMessage2.getReasoning()).isNotEmpty();
		assertThat(assistantMessage2.getText()).isNotEmpty();
	}

	@Test
	void thinkingEnabledTest() {
		var promptOptions = DeepSeekChatOptions.builder().enableThinking().build();
		Prompt prompt = new Prompt("9.11 and 9.8, which is greater?", promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		AssistantMessage assistantMessage = response.getResult().getOutput();
		assertThat(assistantMessage.getReasoning()).isNotEmpty();
		assertThat(assistantMessage.getText()).isNotEmpty();
	}

	@Test
	void thinkingDisabledTest() {
		var promptOptions = DeepSeekChatOptions.builder().disableThinking().build();
		Prompt prompt = new Prompt("9.11 and 9.8, which is greater?", promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		AssistantMessage assistantMessage = response.getResult().getOutput();
		assertThat(assistantMessage.getReasoning()).isEmpty();
		assertThat(assistantMessage.getText()).isNotEmpty();
	}

	@Test
	void reasoningEffortMaxTest() {
		var promptOptions = DeepSeekChatOptions.builder().reasoningEffortMax().build();
		Prompt prompt = new Prompt("9.11 and 9.8, which is greater?", promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		AssistantMessage assistantMessage = response.getResult().getOutput();
		assertThat(assistantMessage.getReasoning()).isNotEmpty();
		assertThat(assistantMessage.getText()).isNotEmpty();
	}

	@Test
	void reasoningEffortHighTest() {
		var promptOptions = DeepSeekChatOptions.builder().reasoningEffortHigh().build();
		Prompt prompt = new Prompt("9.11 and 9.8, which is greater?", promptOptions);
		ChatResponse response = this.chatModel.call(prompt);

		AssistantMessage assistantMessage = response.getResult().getOutput();
		assertThat(assistantMessage.getReasoning()).isNotEmpty();
		assertThat(assistantMessage.getText()).isNotEmpty();
	}

	@Test
	void callReturnsReasoningThenTextParts() {
		var promptOptions = DeepSeekChatOptions.builder().enableThinking().build();
		ChatResponse response = this.chatModel.call(new Prompt("9.11 and 9.8, which is greater?", promptOptions));

		AssistantMessage message = response.getResult().getOutput();
		assertThat(message.getParts()).hasSize(2);
		assertThat(message.getParts().get(0)).isInstanceOfSatisfying(ReasoningPart.class,
				reasoning -> assertThat(reasoning.text()).isNotBlank());
		assertThat(message.getParts().get(1)).isInstanceOf(TextPart.class);
		assertThat(message.getText()).isNotBlank().isEqualTo(((TextPart) message.getParts().get(1)).text());
	}

	@Test
	void streamingChunksCarryIndexedPartsThatAggregate() {
		var promptOptions = DeepSeekChatOptions.builder().enableThinking().build();
		Flux<ChatResponse> flux = this.streamingChatModel
			.stream(new Prompt("9.11 and 9.8, which is greater?", promptOptions));

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		List<ChatResponse> responses = new MessageAggregator().aggregate(flux, aggregatedRef::set)
			.collectList()
			.block();

		// Every part is an indexed delta, except the empty text of a chunk without
		// content, which keeps getText() non-null
		assertThat(responses).allSatisfy(chunk -> {
			assertThat(chunk.getMetadata().getId()).isNotBlank();
			if (chunk.getResult() != null) {
				assertThat(chunk.getResult().getOutput().getText()).isNotNull();
				for (MessagePart part : chunk.getResult().getOutput().getParts()) {
					if (!TextPart.of("").equals(part)) {
						assertThat(StreamingParts.partIndex(part)).isNotNull();
						assertThat(StreamingParts.isPartial(part)).isTrue();
					}
				}
			}
		});
		String streamedText = responses.stream()
			.filter(chunk -> chunk.getResult() != null)
			.map(chunk -> chunk.getResult().getOutput().getText())
			.collect(Collectors.joining());

		AssistantMessage aggregated = aggregatedRef.get().getResult().getOutput();
		assertThat(aggregated.getParts()).hasSize(2);
		assertThat(aggregated.getParts().get(0)).isInstanceOf(ReasoningPart.class);
		assertThat(aggregated.getParts().get(1)).isEqualTo(TextPart.of(streamedText));
		assertThat(aggregated.getParts()).allSatisfy(part -> assertThat(StreamingParts.partIndex(part)).isNull());
	}

	@Test
	void thinkingToolCallTurnIsBuiltFromPartsAndReplayed() {
		// DeepSeek documents that in thinking mode every assistant message of a request
		// with tools must carry its reasoning_content
		DeepSeekChatOptions options = weatherToolOptions();
		Prompt prompt = new Prompt(
				List.of(new UserMessage("What's the weather like in San Francisco, Tokyo, and Paris? Use Celsius.")),
				options);
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		ChatResponse response = this.capturingChatModel.call(prompt);

		AssistantMessage toolCallTurn = response.getResult().getOutput();
		assertThat(toolCallTurn.hasToolCalls()).isTrue();
		// The model sometimes decides on a tool call without any reasoning
		Assumptions.assumeTrue(!toolCallTurn.getReasoning().isEmpty(), "No reasoning for the tool call turn");
		assertThat(toolCallTurn.getParts().get(0)).isInstanceOf(ReasoningPart.class);
		assertThat(toolCallTurn.getParts()).filteredOn(ToolCallPart.class::isInstance)
			.hasSameSizeAs(toolCallTurn.getToolCalls())
			.allSatisfy(part -> assertThat(((ToolCallPart) part).toolCall().name()).isEqualTo("getCurrentWeather"));

		while (response.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
			prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
			response = this.capturingChatModel.call(prompt);
		}
		assertThat(response.getResult().getOutput().getText()).contains("30", "10", "15");
		// The first turn is sent back with its reasoning exactly as it was received
		assertThat(assistantMessagesOfLastRequest().get(0))
			.containsEntry("reasoning_content", reasoningOf(toolCallTurn))
			.containsKey("tool_calls");
	}

	@Test
	void streamingThinkingToolCallTurnIsAggregatedAndReplayed() {
		DeepSeekChatOptions options = weatherToolOptions();
		Prompt prompt = new Prompt(List.of(new UserMessage("What's the weather like in Paris? Use Celsius.")), options);
		ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

		ChatResponse response = streamAndAggregate(prompt);

		AssistantMessage toolCallTurn = response.getResult().getOutput();
		assertThat(toolCallTurn.hasToolCalls()).isTrue();
		// The model sometimes decides on a tool call without any reasoning
		Assumptions.assumeTrue(!toolCallTurn.getReasoning().isEmpty(), "No reasoning for the tool call turn");
		assertThat(toolCallTurn.getReasoning()).hasSize(1);
		assertThat(toolCallTurn.getToolCalls()).allSatisfy(toolCall -> {
			assertThat(toolCall.name()).isEqualTo("getCurrentWeather");
			assertThat(toolCall.arguments()).contains("Paris");
		});

		// The aggregated turn is a plain AssistantMessage, replayed with the reasoning
		// of its ReasoningPart. The next round is a call() so that its request is
		// captured; both kinds of request are built the same way.
		ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, response);
		response = this.capturingChatModel.call(new Prompt(toolExecutionResult.conversationHistory(), options));

		assertThat(assistantMessagesOfLastRequest().get(0)).containsEntry("reasoning_content",
				reasoningOf(toolCallTurn));
		assertThat(response.getResult().getOutput().getText()).contains("15");
	}

	@Test
	void reasoningOfAnEarlierTurnWithoutToolCallsIsSentBack() {
		// DeepSeek documents the replay for every assistant message of a request with
		// tools, including an answer of an earlier turn that called no tool
		DeepSeekChatOptions options = weatherToolOptions();
		List<Message> messages = new ArrayList<>(List.of(new UserMessage("What is 17 * 23? Do not use any tool.")));
		AssistantMessage firstAnswer = this.capturingChatModel.call(new Prompt(messages, options))
			.getResult()
			.getOutput();
		assertThat(firstAnswer.hasToolCalls()).isFalse();
		Assumptions.assumeTrue(!firstAnswer.getReasoning().isEmpty(), "No reasoning for the first answer");

		messages.add(firstAnswer);
		messages.add(new UserMessage("Thanks. What's the weather like in Tokyo? Use Celsius."));
		this.capturingChatModel.call(new Prompt(messages, options));

		List<Map<String, Object>> sentAssistantMessages = assistantMessagesOfLastRequest();
		assertThat(sentAssistantMessages).hasSize(1);
		assertThat(sentAssistantMessages.get(0)).containsEntry("reasoning_content", reasoningOf(firstAnswer));
	}

	private ChatResponse streamAndAggregate(Prompt prompt) {
		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(this.streamingChatModel.stream(prompt), aggregatedRef::set).blockLast();
		return aggregatedRef.get();
	}

	private static String reasoningOf(AssistantMessage message) {
		return message.getReasoning()
			.stream()
			.map(ReasoningPart::text)
			.filter(Objects::nonNull)
			.collect(Collectors.joining());
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> assistantMessagesOfLastRequest() {
		assertThat(this.requestBodies).isNotEmpty();
		Map<String, Object> request = jsonHelper.fromJsonToMap(this.requestBodies.get(this.requestBodies.size() - 1));
		return ((List<Map<String, Object>>) request.get("messages")).stream()
			.filter(message -> "assistant".equals(message.get("role")))
			.toList();
	}

	private static DeepSeekChatOptions weatherToolOptions() {
		return DeepSeekChatOptions.builder()
			.enableThinking()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

}

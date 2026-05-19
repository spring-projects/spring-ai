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

package org.springframework.ai.bedrock.converse;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.bedrock.converse.api.BedrockCacheOptions;
import org.springframework.ai.bedrock.converse.api.BedrockCacheStrategy;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = BedrockConverseTestConfiguration.class)
@RequiresAwsCredentials
class BedrockProxyChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(BedrockProxyChatModelIT.class);

	@Autowired
	protected ChatModel chatModel;

	@Autowired
	protected StreamingChatModel streamingChatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	private static void validateChatResponseMetadata(ChatResponse response, String model) {
		// assertThat(response.getMetadata().getId()).isNotEmpty();
		// assertThat(response.getMetadata().getModel()).containsIgnoringCase(model);
		assertThat(response.getMetadata().getId()).isNotEqualTo("Unknown").isNotBlank();
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
	}

	@ParameterizedTest(name = "{0} : {displayName} ")
	@ValueSource(strings = { "us.anthropic.claude-haiku-4-5-20251001-v1:0", "us.anthropic.claude-sonnet-4-6",
			"us.anthropic.claude-opus-4-6-v1" })
	void roleTest(String modelName) {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage),
				BedrockChatOptions.builder().model(modelName).build());
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isGreaterThan(0);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isGreaterThan(0);
		assertThat(response.getMetadata().getUsage().getTotalTokens())
			.isEqualTo(response.getMetadata().getUsage().getPromptTokens()
					+ response.getMetadata().getUsage().getCompletionTokens());
		Generation generation = response.getResults().get(0);
		assertThat(generation.getOutput().getText()).contains("Blackbeard");
		assertThat(generation.getMetadata().getFinishReason()).isEqualTo("end_turn");
		logger.info(response.toString());
	}

	@Test
	@Disabled
	void testMessageHistory() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(this.systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");

		var promptWithMessageHistory = new Prompt(List.of(new UserMessage("Dummy"), response.getResult().getOutput(),
				new UserMessage("Repeat the last assistant message.")));

		response = this.chatModel.call(promptWithMessageHistory);

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@Test
	void streamingWithTokenUsage() {
		var promptOptions = BedrockChatOptions.builder().temperature(0.0).build();

		var prompt = new Prompt("List two colors of the Polish flag. Be brief.", promptOptions);
		var streamingTokenUsage = this.chatModel.stream(prompt).blockLast().getMetadata().getUsage();

		assertThat(streamingTokenUsage.getPromptTokens()).isGreaterThan(0);
		assertThat(streamingTokenUsage.getCompletionTokens()).isGreaterThan(0);
		assertThat(streamingTokenUsage.getTotalTokens()).isGreaterThan(0);
		assertThat(streamingTokenUsage.getTotalTokens())
			.isEqualTo(streamingTokenUsage.getPromptTokens() + streamingTokenUsage.getCompletionTokens());

	}

	@Test
	void listOutputConverter() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputConverter listOutputConverter = new ListOutputConverter(conversionService);

		String format = listOutputConverter.getFormat();
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

		List<String> list = listOutputConverter.convert(generation.getOutput().getText());
		assertThat(list).hasSize(5);
	}

	@Test
	void mapOutputConverter() {
		MapOutputConverter mapOutputConverter = new MapOutputConverter();

		String format = mapOutputConverter.getFormat();
		String template = """
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

		Map<String, Object> result = mapOutputConverter.convert(generation.getOutput().getText());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	@Test
	void beanOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> beanOutputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = beanOutputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = beanOutputConverter.convert(generation.getOutput().getText());
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> beanOutputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = beanOutputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
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
			.map(AssistantMessage::getText)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = beanOutputConverter.convert(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void multiModalityTest() throws IOException {

		var imageData = new ClassPathResource("/test.png");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)))
			.build();

		var response = this.chatModel.call(new Prompt(List.of(userMessage)));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");
	}

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = BedrockChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location. Return in 36°C format")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		Generation generation = response.getResult();
		assertThat(generation.getOutput().getText()).contains("30", "10", "15");
	}

	@Test
	void functionCallTestWithToolCallingOptions() {

		UserMessage userMessage = new UserMessage(
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = ToolCallingChatOptions.builder()
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description("Get the weather in location. Return in 36°C format")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		Generation generation = response.getResult();
		assertThat(generation.getOutput().getText()).contains("30", "10", "15");
	}

	@Test
	void streamFunctionCallTest() {

		UserMessage userMessage = new UserMessage(
				// "What's the weather like in San Francisco? Return the result in
				// Celsius.");
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = BedrockChatOptions.builder()
			.model("us.anthropic.claude-haiku-4-5-20251001-v1:0")
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));

		String content = response.collectList()
			.block()
			.stream()
			.filter(cr -> cr.getResult() != null)
			.map(cr -> cr.getResult().getOutput().getText())
			.collect(Collectors.joining());

		logger.info("Response: {}", content);
		assertThat(content).contains("30", "10", "15");
	}

	@ParameterizedTest(name = "{displayName} - {0} ")
	@ValueSource(ints = { 50, 60 })
	void streamFunctionCallTestWithMaxTokens(int maxTokens) {

		UserMessage userMessage = new UserMessage(
				// "What's the weather like in San Francisco? Return the result in
				// Celsius.");
				"What's the weather like in San Francisco, Tokyo and Paris? Return the result in Celsius.");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = BedrockChatOptions.builder()
			.maxTokens(maxTokens)
			.model("us.anthropic.claude-haiku-4-5-20251001-v1:0")
			.toolCallbacks(List.of(FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
				.description(
						"Get the weather in location. Return temperature in 36°F or 36°C format. Use multi-turn if needed.")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		Flux<ChatResponse> response = this.chatModel.stream(new Prompt(messages, promptOptions));
		ChatResponse lastResponse = response.blockLast();
		String finishReason = lastResponse.getResult().getMetadata().getFinishReason();

		logger.info("Finish reason: {}", finishReason);
		assertThat(finishReason).isEqualTo("max_tokens");
	}

	@Test
	void validateCallResponseMetadata() {
		String model = "us.anthropic.claude-haiku-4-5-20251001-v1:0";
		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(BedrockChatOptions.builder().model(model))
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.call()
				.chatResponse();
		// @formatter:on

		logger.info(response.toString());
		validateChatResponseMetadata(response, model);
	}

	@Test
	void validateStreamCallResponseMetadata() {
		String model = "us.anthropic.claude-haiku-4-5-20251001-v1:0";
		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(BedrockChatOptions.builder().model(model))
				.user("Tell me about 3 famous pirates from the Golden Age of Piracy and what they did")
				.stream()
				.chatResponse()
				.blockLast();
		// @formatter:on

		logger.info(response.toString());
		validateChatResponseMetadata(response, model);
	}

	@Test
	void testSystemOnlyPromptCaching() {
		// Claude Haiku 4.5 requires 4096+ tokens per cache checkpoint and must be
		// invoked via a cross-region inference profile ID.
		String model = "us.anthropic.claude-haiku-4-5-20251001-v1:0";

		// Each repetition adds ~158 tokens; 40 repetitions = ~6320 tokens, safely
		// exceeding the 4096 token minimum required by Claude Haiku 4.5.
		String basePrompt = """
				You are an expert software architect with deep knowledge of distributed systems,
				microservices, cloud computing, and software design patterns. Your role is to provide
				detailed technical guidance on system architecture, design decisions, and best practices.

				Key areas of expertise:
				- Distributed systems design and architecture
				- Microservices patterns and anti-patterns
				- Cloud-native application development
				- Event-driven architectures
				- Database design and scaling strategies
				- API design and RESTful services
				- Security best practices
				- Performance optimization and scalability

				""";

		// Repeat to exceed 4096 token minimum for Claude Haiku 4.5
		// Using 40 repetitions (~6320 tokens) to safely exceed the threshold

		String largeSystemPrompt = basePrompt.repeat(40)
				+ "When answering questions, provide clear, structured responses with examples.";

		BedrockCacheOptions cacheOptions = BedrockCacheOptions.builder()
			.strategy(BedrockCacheStrategy.SYSTEM_ONLY)
			.build();

		BedrockChatOptions chatOptions = BedrockChatOptions.builder()
			.model(model)
			.cacheOptions(cacheOptions)
			.maxTokens(500)
			.build();

		// Send requests with the same system prompt until a cache read is observed.
		// With cross-region inference profiles, initial requests may route to different
		// regions and each write their own cache. Eventually a request will route to a
		// region with an existing cache and return a positive cacheReadInputTokens.
		List<String> questions = List.of("What is a monolith?", "What is a microservice?",
				"What is event-driven architecture?", "What is a service mesh?", "What is CQRS?");
		AtomicInteger questionIndex = new AtomicInteger(0);
		Awaitility.await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(3)).untilAsserted(() -> {
			String question = questions.get(questionIndex.getAndIncrement() % questions.size());
			ChatResponse response = this.chatModel.call(
					new Prompt(List.of(new SystemMessage(largeSystemPrompt), new UserMessage(question)), chatOptions));
			assertThat(response.getResults()).hasSize(1);
			assertThat(response.getResult().getOutput().getText()).isNotEmpty();
			Integer cacheRead = response.getMetadata().get("cacheReadInputTokens");
			Integer cacheWrite = response.getMetadata().get("cacheWriteInputTokens");
			logger.info("[systemOnly] attempt={}, cacheWrite={}, cacheRead={}", questionIndex.get(), cacheWrite,
					cacheRead);
			assertThat(cacheRead).as("Should eventually read from cache").isNotNull().isPositive();
			assertThat(cacheRead).as("Cache read should meet the 4096 token minimum for Claude Haiku 4.5")
				.isGreaterThan(4096);
			assertThat(cacheWrite).as("A cache read hit should not also write").isIn(null, 0);

			// Verify unified Usage interface reports the same cache metrics
			org.springframework.ai.chat.metadata.Usage springUsage = response.getMetadata().getUsage();
			assertThat(springUsage.getCacheReadInputTokens())
				.as("Usage interface should report same cache read tokens as metadata")
				.isEqualTo(cacheRead.longValue());
		});
	}

	@Test
	void testToolsOnlyPromptCaching() {
		// IMPORTANT: This test requires a Claude model - Amazon Nova models do NOT
		// support tool caching and will return ValidationException.
		// Claude Haiku 4.5 requires 4096+ tokens of tool definitions for caching.
		String model = "us.anthropic.claude-haiku-4-5-20251001-v1:0";

		// Create multiple tool callbacks to exceed the 4096 token minimum for caching
		// (Claude Haiku 4.5 requires 4096+ tokens)
		// Each tool definition adds ~200-300 tokens, so we need 4-5 tools
		List<FunctionToolCallback> toolCallbacks = createLargeToolCallbacks();

		BedrockCacheOptions cacheOptions = BedrockCacheOptions.builder()
			.strategy(BedrockCacheStrategy.TOOLS_ONLY)
			.build();

		BedrockChatOptions chatOptions = BedrockChatOptions.builder()
			.model(model)
			.cacheOptions(cacheOptions)
			.toolCallbacks(List.copyOf(toolCallbacks))
			.maxTokens(500)
			.build();

		// Send requests with the same tools until a cache read is observed.
		// With cross-region inference profiles, initial requests may write to different
		// regions. Eventually a request will hit a region with an existing cache.
		List<String> cities = List.of("Paris", "Tokyo", "London", "New York", "Sydney");
		AtomicInteger cityIndex = new AtomicInteger(0);
		Awaitility.await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(3)).untilAsserted(() -> {
			String city = cities.get(cityIndex.getAndIncrement() % cities.size());
			ChatResponse response = this.chatModel.call(new Prompt("What's the weather in " + city + "?", chatOptions));
			assertThat(response.getResults()).hasSize(1);
			assertThat(response.getResult().getOutput().getText()).isNotEmpty();
			Integer cacheRead = response.getMetadata().get("cacheReadInputTokens");
			Integer cacheWrite = response.getMetadata().get("cacheWriteInputTokens");
			logger.info("[toolsOnly] attempt={}, cacheWrite={}, cacheRead={}", cityIndex.get(), cacheWrite, cacheRead);
			assertThat(cacheRead).as("Should eventually read tool definitions from cache").isNotNull().isPositive();
			assertThat(cacheRead).as("Cache read should meet the 4096 token minimum for Claude Haiku 4.5")
				.isGreaterThan(4096);
			assertThat(cacheWrite).as("A cache read hit should not also write").isIn(null, 0);
		});
	}

	@Test
	void testSystemAndToolsPromptCaching() {
		// NOTE: Testing combined caching requires both large system prompt and multiple
		// tools
		// IMPORTANT: This test requires a Claude model that supports tool caching.
		// Amazon Nova models do NOT support tool caching and will return
		// ValidationException
		String model = "us.anthropic.claude-haiku-4-5-20251001-v1:0";

		// Create large system prompt (1K+ tokens)
		String basePrompt = """
				You are an expert weather analyst with deep knowledge of meteorology,
				climate patterns, and weather forecasting. Your role is to provide detailed
				weather analysis and recommendations.

				Key areas of expertise:
				- Weather pattern analysis and forecasting
				- Climate change impacts on weather
				- Severe weather prediction and safety
				- Seasonal weather trends
				- Microclimate analysis
				- Weather data interpretation
				- Agricultural weather impacts
				- Travel and event weather planning

				""";

		String largeSystemPrompt = basePrompt.repeat(12)
				+ "Provide detailed weather analysis with context and recommendations.";

		// Create multiple tool callbacks
		List<FunctionToolCallback> toolCallbacks = createLargeToolCallbacks();

		BedrockCacheOptions cacheOptions = BedrockCacheOptions.builder()
			.strategy(BedrockCacheStrategy.SYSTEM_AND_TOOLS)
			.build();

		BedrockChatOptions chatOptions = BedrockChatOptions.builder()
			.model(model)
			.cacheOptions(cacheOptions)
			.toolCallbacks(List.copyOf(toolCallbacks))
			.maxTokens(500)
			.build();

		// Send requests with the same tools and system prompt until a cache read is
		// observed. With cross-region inference profiles, initial requests may write to
		// different regions. Eventually a request will hit a region with an existing
		// cache.
		List<String> cities = List.of("Paris", "Tokyo", "London", "New York", "Sydney");
		AtomicInteger cityIndex = new AtomicInteger(0);
		Awaitility.await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(3)).untilAsserted(() -> {
			String city = cities.get(cityIndex.getAndIncrement() % cities.size());
			ChatResponse response = this.chatModel.call(new Prompt(List.of(new SystemMessage(largeSystemPrompt),
					new UserMessage("What's the weather in " + city + "?")), chatOptions));
			assertThat(response.getResults()).hasSize(1);
			assertThat(response.getResult().getOutput().getText()).isNotEmpty();
			Integer cacheRead = response.getMetadata().get("cacheReadInputTokens");
			Integer cacheWrite = response.getMetadata().get("cacheWriteInputTokens");
			logger.info("[systemAndTools] attempt={}, cacheWrite={}, cacheRead={}", cityIndex.get(), cacheWrite,
					cacheRead);
			assertThat(cacheRead).as("Should eventually read from cache").isNotNull().isPositive();
			assertThat(cacheRead).as("Cache read should meet the 4096 token minimum for Claude Haiku 4.5")
				.isGreaterThan(4096);
			assertThat(cacheWrite).as("A cache read hit should not also write").isIn(null, 0);
		});
	}

	@Test
	void testConversationHistoryPromptCachingWithClaude() {
		// NOTE: Conversation history caching is verified to work with Claude models
		// Amazon Nova models theoretically support this but haven't been verified in
		// tests
		String model = "us.anthropic.claude-haiku-4-5-20251001-v1:0";

		// Create a large system prompt to contribute to total token count
		// Claude Haiku 4.5 requires 4096+ tokens for caching to activate

		// A modest system prompt is sufficient here; the messages field provides the
		// token volume needed for the cache checkpoint (via verbose assistant turns
		// below).
		String largeSystemPrompt = """
				You are a helpful AI assistant with expertise in career counseling and professional development.
				You remember details from our conversation and use them to provide personalized responses.
				Always acknowledge information shared by the user in previous messages when relevant to the current question.
				Your advice should be specific, actionable, and tailored to the user's background, industry, and goals.
				When providing career guidance, consider market trends, skill development, networking, and work-life balance.
				""";

		// Build conversation history with verbose assistant responses so the messages
		// field alone exceeds the 4,096 token minimum required by Claude Haiku 4.5 for a
		// cache checkpoint. The system prompt lives in the system field and does not
		// count
		// toward the messages cache checkpoint threshold.
		String verboseAssistantTurn = """
				That's really fascinating to hear! Let me share some detailed thoughts on your situation.
				Working in data science at a tech company in San Francisco puts you at the forefront of
				innovation. The combination of machine learning and natural language processing is
				particularly powerful right now, given the explosion of large language models and
				transformer-based architectures. San Francisco's tech ecosystem offers unparalleled
				networking opportunities, access to cutting-edge research, and exposure to world-class
				engineering talent. The recommendation systems space is especially exciting because it
				sits at the intersection of multiple disciplines: collaborative filtering, content-based
				methods, matrix factorization, deep learning, and reinforcement learning from human
				feedback. Companies like Netflix, Spotify, Amazon, and LinkedIn have published
				extensively on their recommendation architectures, and the field continues to evolve
				rapidly. Building production-grade recommendation systems requires not just modeling
				skills but also expertise in data pipelines, feature engineering, A/B testing
				frameworks, and real-time serving infrastructure. The ability to measure business
				impact through metrics like click-through rate, conversion rate, and long-term
				engagement is equally important. I'd be happy to dive deeper into any of these areas.
				""".repeat(15);

		List<Message> conversationHistory = new ArrayList<>();
		conversationHistory.add(new SystemMessage(largeSystemPrompt));
		conversationHistory
			.add(new UserMessage("My name is Alice and I work as a data scientist at TechCorp in San Francisco."));
		conversationHistory.add(new AssistantMessage(verboseAssistantTurn));
		conversationHistory.add(new UserMessage(
				"I've been there for 3 years. I specialize in machine learning and natural language processing."));
		conversationHistory.add(new AssistantMessage(verboseAssistantTurn));
		conversationHistory.add(new UserMessage(
				"Recently I've been building a recommendation system that analyzes user behavior and preferences."));
		conversationHistory.add(new AssistantMessage(verboseAssistantTurn));

		// The cache point is placed on this final user message by CONVERSATION_HISTORY
		// strategy. All preceding messages form the cached prefix. With 3 assistant turns
		// at 8 repetitions each (~560 tokens/turn), the prefix exceeds the 4,096 token
		// minimum required by Claude Haiku 4.5 for a messages cache checkpoint.
		conversationHistory
			.add(new UserMessage("Based on what I've told you about my work, what career advice would you give me?"));

		BedrockCacheOptions cacheOptions = BedrockCacheOptions.builder()
			.strategy(BedrockCacheStrategy.CONVERSATION_HISTORY)
			.build();

		BedrockChatOptions chatOptions = BedrockChatOptions.builder()
			.model(model)
			.cacheOptions(cacheOptions)
			.maxTokens(500)
			.build();

		// Send the identical conversation history on every attempt until a cache read is
		// observed. The cache key is derived from the full message list including the
		// last
		// user message, so the prompt must be byte-for-byte identical across attempts for
		// a cross-region hit to occur. With cross-region inference profiles, initial
		// requests may write to different regions; eventually a request will route to a
		// region that already has the cache and return a positive cacheReadInputTokens.
		AtomicInteger attemptIndex = new AtomicInteger(0);
		Awaitility.await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(3)).untilAsserted(() -> {
			ChatResponse response = this.chatModel.call(new Prompt(conversationHistory, chatOptions));
			assertThat(response.getResults()).hasSize(1);
			assertThat(response.getResult().getOutput().getText()).isNotEmpty();
			Integer cacheRead = response.getMetadata().get("cacheReadInputTokens");
			Integer cacheWrite = response.getMetadata().get("cacheWriteInputTokens");
			logger.info("[conversationHistory] attempt={}, cacheWrite={}, cacheRead={}", attemptIndex.incrementAndGet(),
					cacheWrite, cacheRead);
			assertThat(cacheRead).as("Should eventually read conversation history from cache").isNotNull().isPositive();
			assertThat(cacheRead).as("Cache read should meet the 4096 token minimum for Claude Haiku 4.5")
				.isGreaterThan(4096);
			assertThat(cacheWrite).as("A cache read hit should not also write").isIn(null, 0);
		});
	}

	/**
	 * Helper method to create multiple tool callbacks with descriptions large enough to
	 * exceed the 4096 token minimum required by Claude Haiku 4.5 for prompt caching.
	 * Creates 5 different weather-related tools with repeated verbose descriptions.
	 */
	private List<FunctionToolCallback> createLargeToolCallbacks() {
		// Each description is repeated to ensure total tool tokens exceed 4096,
		// which is the minimum required by Claude Haiku 4.5 for prompt caching.
		String weatherDesc = """
				Get the current weather conditions for a specific location anywhere in the world.
				This comprehensive weather service provides real-time meteorological data including:
				- Current temperature in Celsius and Fahrenheit with feels-like temperature
				- Humidity levels and dew point information
				- Atmospheric pressure readings (both sea level and station pressure)
				- Wind speed, direction, and gusts information
				- Cloud coverage percentage and type (cumulus, stratus, cirrus, etc.)
				- Visibility distance in kilometers and miles
				- Current precipitation status (rain, snow, sleet, hail)
				- UV index and solar radiation levels
				- Air quality index (AQI) and pollutant concentrations
				- Sunrise and sunset times for the location
				The service uses data from multiple meteorological stations and satellites to ensure
				accuracy and reliability. Data is updated every 15 minutes for most locations worldwide.
				""".repeat(10);
		String forecastDesc = """
				Get the weather forecast for the next 7 days for a specific location with detailed predictions.
				This advanced forecasting service provides comprehensive weather predictions including:
				- Daily high and low temperatures with hourly breakdowns
				- Precipitation probability percentage for each day and hour
				- Expected precipitation amounts (rain, snow) in millimeters and inches
				- Wind forecasts including speed, direction, and gust predictions
				- Cloud coverage predictions and sky conditions (sunny, partly cloudy, overcast)
				- Humidity levels and heat index/wind chill calculations
				- Severe weather warnings and advisories if applicable
				- Sunrise and sunset times for each day
				- Moon phase information for planning outdoor activities
				- Detailed text descriptions of expected conditions for each day
				The forecast uses advanced meteorological models combining numerical weather prediction,
				machine learning algorithms, and historical climate data to provide highly accurate
				predictions. Forecasts are updated four times daily with improving accuracy for near-term
				predictions and reasonable accuracy extending to 7 days out.
				""".repeat(10);
		String historicalDesc = """
				Get historical weather data for a specific location and date range with comprehensive analysis.
				This powerful historical weather service provides access to decades of weather records including:
				- Temperature records: daily highs, lows, and averages for any date range
				- Precipitation history: rainfall and snowfall amounts with accumulation totals
				- Temperature trend analysis comparing to long-term averages and records
				- Extreme weather events: heat waves, cold snaps, severe storms in the time period
				- Climate comparisons showing how conditions compare to historical norms
				- Monthly and seasonal summaries with statistical analysis
				- Detailed day-by-day weather observations from official weather stations
				- Notable weather events and their impacts during the requested time period
				The historical data is sourced from official meteorological agencies and weather stations
				with records extending back multiple decades. This tool is invaluable for understanding
				climate trends, planning activities based on historical patterns, agricultural planning,
				research purposes, and understanding how current weather compares to historical context.
				Data quality indicators are provided to show the reliability of older records.
				""".repeat(10);
		String alertsDesc = """
				Get active weather alerts and warnings for a specific location with critical safety information.
				This essential safety service provides real-time alerts from official meteorological services including:
				- Severe thunderstorm warnings with timing and intensity information
				- Tornado warnings and watches with affected areas and safety instructions
				- Hurricane and tropical storm alerts with projected paths and wind speeds
				- Flash flood warnings and flood watches with affected waterways
				- Winter storm warnings including snow, ice, and blizzard conditions
				- Heat advisories and excessive heat warnings with health recommendations
				- Wind advisories and high wind warnings with expected peak gusts
				- Dense fog advisories affecting visibility and travel
				- Air quality alerts for unhealthy pollution levels
				- Fire weather warnings for dangerous wildfire conditions
				Each alert includes the official alert level (advisory, watch, warning), affected geographic
				areas, start and end times, detailed descriptions of the hazard, recommended actions for
				safety, and contact information for local emergency management. Alerts are issued by
				official national weather services and are updated in real-time as conditions evolve.
				This service is critical for public safety and emergency preparedness.
				""".repeat(10);
		String climateDesc = """
				Get long-term climate data and comprehensive statistics for a specific location.
				This climate analysis service provides in-depth climatological information including:
				- Long-term average temperatures: monthly and annual means over 30+ year periods
				- Precipitation patterns: average rainfall and snowfall by month and season
				- Seasonal trend analysis showing typical weather patterns throughout the year
				- Climate classification according to Köppen-Geiger system
				- Record high and low temperatures for each month with dates
				- Average humidity levels, cloud coverage, and sunshine hours
				- Wind patterns including prevailing wind directions and average speeds
				- Growing season length and frost dates important for agriculture
				- Climate change indicators showing temperature and precipitation trends
				- Extreme weather frequency: how often severe events typically occur
				- Comparison with global and regional climate averages
				- Microclimate variations within the region based on elevation and geography
				- Best and worst months for various outdoor activities based on climate
				This comprehensive climate data is essential for long-term planning, understanding regional
				climate characteristics, agricultural planning, construction projects, tourism planning,
				and understanding local climate change impacts. Data is derived from decades of official
				meteorological observations and is continuously updated as new climate normals are established.
				""".repeat(10);
		return List.of(
				FunctionToolCallback.builder("getCurrentWeather", new MockWeatherService())
					.description(weatherDesc)
					.inputType(MockWeatherService.Request.class)
					.build(),
				FunctionToolCallback.builder("getWeatherForecast", new MockWeatherService())
					.description(forecastDesc)
					.inputType(MockWeatherService.Request.class)
					.build(),
				FunctionToolCallback.builder("getHistoricalWeather", new MockWeatherService())
					.description(historicalDesc)
					.inputType(MockWeatherService.Request.class)
					.build(),
				FunctionToolCallback.builder("getWeatherAlerts", new MockWeatherService())
					.description(alertsDesc)
					.inputType(MockWeatherService.Request.class)
					.build(),
				FunctionToolCallback.builder("getClimateData", new MockWeatherService())
					.description(climateDesc)
					.inputType(MockWeatherService.Request.class)
					.build());
	}

	@Test
	void testOpenAIGptOssModelResponse() {
		// Test for OpenAI gpt-oss models on Bedrock which return ReasoningContent + Text
		// blocks
		// This test verifies the fix for null responses when gpt-oss models return
		// multiple
		// ContentBlocks
		String model = "openai.gpt-oss-120b-1:0";

		UserMessage userMessage = new UserMessage("What is 2+2? Answer briefly.");
		Prompt prompt = new Prompt(List.of(userMessage), BedrockChatOptions.builder().model(model).build());

		ChatResponse response = this.chatModel.call(prompt);

		// Verify response is not null and contains expected content
		assertThat(response.getResults()).hasSize(1);
		Generation generation = response.getResults().get(0);

		// The key assertion: response text should NOT be null
		assertThat(generation.getOutput().getText()).as("gpt-oss model should return non-null text content")
			.isNotNull()
			.isNotEmpty();

		// Verify the response contains the expected answer
		assertThat(generation.getOutput().getText()).as("gpt-oss should correctly answer the math question")
			.containsAnyOf("4", "four");

		// Verify metadata
		assertThat(generation.getMetadata().getFinishReason()).isEqualTo("end_turn");
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();

		logger.info("gpt-oss Response: {}", generation.getOutput().getText());
		logger.info("Response metadata: {}", response.getMetadata());
	}

	@Test
	void testOpenAIGptOssModelStreamingResponse() {
		// Test streaming with OpenAI gpt-oss models to ensure ReasoningContent blocks are
		// handled correctly
		String model = "openai.gpt-oss-120b-1:0";

		UserMessage userMessage = new UserMessage("Who are you?");
		Prompt prompt = new Prompt(List.of(userMessage), BedrockChatOptions.builder().model(model).build());

		Flux<ChatResponse> responseFlux = this.chatModel.stream(prompt);

		String fullResponse = responseFlux.collectList()
			.block()
			.stream()
			.filter(cr -> cr.getResult() != null)
			.map(cr -> cr.getResult().getOutput().getText())
			.collect(Collectors.joining());

		// Verify streaming response is not null or empty
		assertThat(fullResponse).as("gpt-oss streaming response should not be null or empty").isNotNull().isNotEmpty();

		// Verify the response contains expected gpt-oss identification
		assertThat(fullResponse.toLowerCase()).as("gpt-oss model should identify itself")
			.containsAnyOf("chatgpt", "gpt", "openai", "language model", "ai");

		logger.info("gpt-oss Streaming Response: {}", fullResponse);
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

}

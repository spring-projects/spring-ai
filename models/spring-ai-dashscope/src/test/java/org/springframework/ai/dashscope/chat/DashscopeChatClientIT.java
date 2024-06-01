package org.springframework.ai.dashscope.chat;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.dashscope.api.DashscopeApi;
import org.springframework.ai.dashscope.DashscopeChatModel;
import org.springframework.ai.dashscope.DashscopeChatOptions;
import org.springframework.ai.dashscope.api.tool.MockWeatherService;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.ai.parser.MapOutputParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Nottyjay Ji
 */
@SpringBootTest
public class DashscopeChatClientIT {

	private static final Logger logger = LoggerFactory.getLogger(DashscopeChatClientIT.class);

	@Autowired
	private DashscopeChatModel chatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void roleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
		ChatResponse response = chatModel.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getContent()).contains("Blackbeard");
	}

	@Test
	void outputParser() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputParser outputParser = new ListOutputParser(conversionService);

		String format = outputParser.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "ice cream flavors", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		List<String> list = outputParser.parse(generation.getOutput().getContent());
		assertThat(list).hasSize(5);

	}

	@Test
	void mapOutputParser() {
		MapOutputParser outputParser = new MapOutputParser();

		String format = outputParser.getFormat();
		String template = """
				Provide me a List of {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = chatModel.call(prompt).getResult();
		String generationText = generation.getOutput().getContent().replace("```json", "").replace("```", "");

		Map<String, Object> result = outputParser.parse(generationText);
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	@Test
	void beanStreamOutputParserRecords() {

		BeanOutputParser<ActorsFilmsRecord> outputParser = new BeanOutputParser<>(ActorsFilmsRecord.class);

		String format = outputParser.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}.
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		String generationTextFromStream = chatModel.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());
		generationTextFromStream = generationTextFromStream.replace("```json", "").replace("```", "");

		ActorsFilmsRecord actorsFilms = outputParser.parse(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

	@Test
	void functionCallTest() {

		UserMessage userMessage = new UserMessage("What's the weather like in San Francisco?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = DashscopeChatOptions.builder()
			.withModel(DashscopeApi.ChatModel.QWEN_MAX.getModel())
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("getCurrentWeather")
				.withDescription("Get the weather in location")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build()))
			.build();

		ChatResponse response = chatModel.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("30.0", "30");
	}

	// @Test
	// void streamFunctionCallTest() {
	//
	// UserMessage userMessage = new UserMessage("What's the weather like Paris?");
	//
	// List<Message> messages = new ArrayList<>(List.of(userMessage));
	//
	// var promptOptions = DashscopeChatOptions.builder()
	// .withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new
	// MockWeatherService())
	// .withName("getCurrentWeather")
	// .withDescription("Get the weather in location")
	// .withResponseConverter((response) -> "" + response.temp() + response.unit())
	// .build()))
	// .build();
	//
	// Flux<ChatResponse> response = chatClient.stream(new Prompt(messages,
	// promptOptions));
	//
	// String content = response.collectList()
	// .block()
	// .stream()
	// .map(ChatResponse::getResults)
	// .flatMap(List::stream)
	// .map(Generation::getOutput)
	// .map(AssistantMessage::getContent)
	// .collect(Collectors.joining());
	// logger.info("Response: {}", content);
	//
	// assertThat(content).containsAnyOf("15.0", "15");
	// }

	@Test
	void usageInStream() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputParser outputParser = new ListOutputParser(conversionService);

		String format = outputParser.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "ice cream flavors", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		List<ChatResponse> responses = this.chatModel.stream(prompt).collectList().block();
		ChatResponse response = responses.get(responses.size() - 1);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(47);
	}

}

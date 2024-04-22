package org.springframework.ai.zhipuai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.model.function.FunctionCallbackWrapper;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.ai.parser.MapOutputParser;
import org.springframework.ai.zhipuai.api.ZhipuAiApi;
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
 * @author Ricken Bazolo
 */
@SpringBootTest(classes = ZhipuAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".+")
class ZhipuAiChatClientIT {

	private static final Logger logger = LoggerFactory.getLogger(ZhipuAiChatClientIT.class);

	@Autowired
	protected ChatClient chatClient;

	@Autowired
	protected StreamingChatClient streamingChatClient;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Value("classpath:/prompts/eval/qa-evaluator-accurate-answer.st")
	protected Resource qaEvaluatorAccurateAnswerResource;

	@Value("classpath:/prompts/eval/qa-evaluator-not-related-message.st")
	protected Resource qaEvaluatorNotRelatedResource;

	@Value("classpath:/prompts/eval/qa-evaluator-fact-based-answer.st")
	protected Resource qaEvalutaorFactBasedAnswerResource;

	@Value("classpath:/prompts/eval/user-evaluator-message.st")
	protected Resource userEvaluatorResource;

	@Test
	void roleTest() {
		var userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		var systemPromptTemplate = new SystemPromptTemplate(systemResource);
		var systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		var prompt = new Prompt(List.of(systemMessage, userMessage));
		var response = chatClient.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getContent()).contains("Blackbeard");
	}

	@Test
	void outputParser() {
		var conversionService = new DefaultConversionService();
		var outputParser = new ListOutputParser(conversionService);

		var format = outputParser.getFormat();
		var template = """
				List five {subject}
				{format}
				""";
		var promptTemplate = new PromptTemplate(template, Map.of("subject", "ice cream flavors", "format", format));
		var prompt = new Prompt(promptTemplate.createMessage());
		var generation = this.chatClient.call(prompt).getResult();

		var list = outputParser.parse(generation.getOutput().getContent());
		assertThat(list).hasSize(5);
	}

	@Test
	void mapOutputParser() {
		var outputParser = new MapOutputParser();

		var format = outputParser.getFormat();
		var template = """
				Provide me a List of {subject}
				{format}
				""";
		var promptTemplate = new PromptTemplate(template,
				Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format", format));
		var prompt = new Prompt(promptTemplate.createMessage());
		var generation = chatClient.call(prompt).getResult();

		var result = outputParser.parse(getJsonStringFromMarkdown(generation.getOutput().getContent()));
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

	@Test
	void beanOutputParserRecords() {

		var outputParser = new BeanOutputParser<>(ActorsFilmsRecord.class);

		var format = outputParser.getFormat();
		var template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		var promptTemplate = new PromptTemplate(template, Map.of("format", format));
		var prompt = new Prompt(promptTemplate.createMessage());
		var generation = chatClient.call(prompt).getResult();

		var actorsFilms = outputParser.parse(getJsonStringFromMarkdown(generation.getOutput().getContent()));
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputParserRecords() {

		var outputParser = new BeanOutputParser<>(ActorsFilmsRecord.class);

		var format = outputParser.getFormat();
		var template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		var promptTemplate = new PromptTemplate(template, Map.of("format", format));
		var prompt = new Prompt(promptTemplate.createMessage());

		var generationTextFromStream = streamingChatClient.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		var actorsFilms = outputParser.parse(getJsonStringFromMarkdown(generationTextFromStream));
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void functionCallTest() {

		var userMessage = new UserMessage("What's the weather like in San Francisco?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = ZhipuAiChatOptions.builder()
			.withModel(ZhipuAiApi.ChatModel.GLM_4.getValue())
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("getCurrentWeather")
				.withDescription("Get the weather in location")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build()))
			.build();

		var response = chatClient.call(new Prompt(messages, promptOptions));

		logger.info("Response: {}", response);

		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("30.0", "30");
	}

	@Test
	void streamFunctionCallTest() {

		var userMessage = new UserMessage("What's the weather like in Tokyo, Japan?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		var promptOptions = ZhipuAiChatOptions.builder()
			.withModel(ZhipuAiApi.ChatModel.GLM_4.getValue())
			.withFunctionCallbacks(List.of(FunctionCallbackWrapper.builder(new MockWeatherService())
				.withName("getCurrentWeather")
				.withDescription("Get the weather in location")
				.withResponseConverter((response) -> "" + response.temp() + response.unit())
				.build()))
			.build();

		var response = streamingChatClient.stream(new Prompt(messages, promptOptions));

		var content = response.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(content).containsAnyOf("10.0", "10");
	}

	private String getJsonStringFromMarkdown(String markdown) {
		return markdown.replaceAll("```json", "").replaceAll("```", "").trim();
	}

}

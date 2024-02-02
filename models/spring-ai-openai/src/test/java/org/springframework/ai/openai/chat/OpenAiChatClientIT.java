package org.springframework.ai.openai.chat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.metadata.support.MyFunctionService;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.ai.parser.ListOutputParser;
import org.springframework.ai.parser.MapOutputParser;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiChatClientIT extends AbstractIT {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Autowired
	@Qualifier("openAiChatClient")
	OpenAiChatClient openAiChatClientSupportingFunctionCalling;

	@Test
	void roleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		ChatResponse response = openAiChatClient.call(prompt);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput().getContent()).contains("Blackbeard");
		// needs fine tuning... evaluateQuestionAndAnswer(request, response, false);
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
		Generation generation = this.openAiChatClient.call(prompt).getResult();

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
		Generation generation = openAiChatClient.call(prompt).getResult();

		Map<String, Object> result = outputParser.parse(generation.getOutput().getContent());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	@Test
	void beanOutputParser() {

		BeanOutputParser<ActorsFilms> outputParser = new BeanOutputParser<>(ActorsFilms.class);

		String format = outputParser.getFormat();
		String template = """
				Generate the filmography for a random actor.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = openAiChatClient.call(prompt).getResult();

		ActorsFilms actorsFilms = outputParser.parse(generation.getOutput().getContent());
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

	@Test
	void beanOutputParserRecords() {

		BeanOutputParser<ActorsFilmsRecord> outputParser = new BeanOutputParser<>(ActorsFilmsRecord.class);

		String format = outputParser.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = openAiChatClient.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputParser.parse(generation.getOutput().getContent());
		System.out.println(actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputParserRecords() {

		BeanOutputParser<ActorsFilmsRecord> outputParser = new BeanOutputParser<>(ActorsFilmsRecord.class);

		String format = outputParser.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		String generationTextFromStream = openStreamingChatClient.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputParser.parse(generationTextFromStream);
		System.out.println(actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	/**
	 *
	 * Sample output from gpt-4-1106-preview model for the query:
	 *
	 * Message systemMessage = new SystemMessage( "I live in the US, so use the units of
	 * temperature and currency that are applicable for Americans.");
	 *
	 * String query = "I love playing golf when it's not too cold or hot. I live in
	 * Pittsburgh, PA and can travel anywhere in the country to play. Where should I go "
	 * + "where it is pleasant and economical based on airfares?"; UserMessage userMessage
	 * = new UserMessage(query);
	 *
	 *
	 * 11:09:50.084 [main] DEBUG o.s.ai.openai.OpenAiChatClient - Response: null
	 * 11:09:50.086 [main] DEBUG o.s.a.o.m.support.MyFunctionService - Calling
	 * getWeatherForLocation with location Pittsburgh, PA 11:10:53.526 [main] DEBUG
	 * o.s.ai.openai.OpenAiChatClient - Response: Playing golf in pleasant weather
	 * typically means looking for temperatures that are neither too cold nor too hot.
	 * Many golfers find temperatures between 60°F to 80°F comfortable. Currently, in
	 * Pittsburgh, PA, the temperature is around 60°F, and it's cloudy, which could be
	 * considered on the cooler side of pleasant for golfing.
	 *
	 * To find a destination with pleasant weather and economical airfares, we can look
	 * for cities where the current temperatures are within this range and then check the
	 * airfares from Pittsburgh, PA, to those destinations. Let's find a few locations
	 * with suitable weather conditions and then compare the airfares to find the most
	 * economical option.
	 *
	 * We'll proceed by selecting a few destinations that are known for their golf courses
	 * and checking both their weather and airfares from Pittsburgh, PA. Here are some
	 * popular golfing destinations we could consider:
	 *
	 * 1. San Diego, CA 2. Phoenix, AZ 3. Orlando, FL 4. Myrtle Beach, SC 5. Las Vegas, NV
	 *
	 * Let's check the weather and airfares for these locations. 11:10:53.527 [main] DEBUG
	 * o.s.a.o.m.support.MyFunctionService - Calling getWeatherForLocation with location
	 * San Diego, CA 11:10:53.527 [main] DEBUG o.s.a.o.m.support.MyFunctionService -
	 * Calling getWeatherForLocation with location Phoenix, AZ 11:10:53.527 [main] DEBUG
	 * o.s.a.o.m.support.MyFunctionService - Calling getWeatherForLocation with location
	 * Orlando, FL 11:10:53.527 [main] DEBUG o.s.a.o.m.support.MyFunctionService - Calling
	 * getWeatherForLocation with location Myrtle Beach, SC 11:10:53.527 [main] DEBUG
	 * o.s.a.o.m.support.MyFunctionService - Calling getWeatherForLocation with location
	 * Las Vegas, NV 11:10:53.528 [main] DEBUG o.s.a.o.m.support.MyFunctionService -
	 * Calling getAirfareForLocation with destination San Diego, CA 11:10:53.528 [main]
	 * DEBUG o.s.a.o.m.support.MyFunctionService - Calling getAirfareForLocation with
	 * destination Phoenix, AZ 11:10:53.528 [main] DEBUG
	 * o.s.a.o.m.support.MyFunctionService - Calling getAirfareForLocation with
	 * destination Orlando, FL 11:10:53.528 [main] DEBUG
	 * o.s.a.o.m.support.MyFunctionService - Calling getAirfareForLocation with
	 * destination Myrtle Beach, SC 11:10:53.528 [main] DEBUG
	 * o.s.a.o.m.support.MyFunctionService - Calling getAirfareForLocation with
	 * destination Las Vegas, NV 11:11:04.887 [main] DEBUG o.s.ai.openai.OpenAiChatClient
	 * - Response: Based on current weather and airfare, here are some options for you:
	 *
	 * 1. **San Diego, CA:** The weather is currently gloomy at around 40°F, which might
	 * be too cold for playing golf. However, the airfare is quite economical at $150 USD.
	 *
	 * 2. **Phoenix, AZ:** It's sunny with a pleasant temperature of 78°F, ideal for golf.
	 * The airfare is a bit higher at $305 USD.
	 *
	 * 3. **Orlando, FL:** Also sunny with a nice temperature of 75°F. The airfare is
	 * close to Phoenix at $300 USD.
	 *
	 * 4. **Myrtle Beach, SC:** Currently the weather is gloomy at 40°F, similar to San
	 * Diego, and not ideal for golf. The airfare is economical at $150 USD.
	 *
	 * 5. **Las Vegas, NV:** The weather is gloomy at 40°F, much like San Diego and Myrtle
	 * Beach, and the airfare is $150 USD.
	 *
	 * For pleasant weather suitable for playing golf, **Phoenix, AZ** and **Orlando, FL**
	 * are your best bets right now, with Phoenix being slightly warmer. However, Phoenix
	 * is also the most expensive in terms of airfare. If you're looking for a balance
	 * between economical travel and good weather, **Orlando, FL** might be the better
	 * choice, as it has similar weather to Phoenix but the airfare is slightly less.
	 * 11:11:04.898 [main] DEBUG o.s.a.openai.chat.OpenAiChatClientIT - Final Answer: {
	 * "content" : "Based on current weather and airfare, here are some options for
	 * you:\n\n1. **San Diego, CA:** The weather is currently gloomy at around 40°F, which
	 * might be too cold for playing golf. However, the airfare is quite economical at
	 * $150 USD.\n\n2. **Phoenix, AZ:** It's sunny with a pleasant temperature of 78°F,
	 * ideal for golf. The airfare is a bit higher at $305 USD.\n\n3. **Orlando, FL:**
	 * Also sunny with a nice temperature of 75°F. The airfare is close to Phoenix at $300
	 * USD.\n\n4. **Myrtle Beach, SC:** Currently the weather is gloomy at 40°F, similar
	 * to San Diego, and not ideal for golf. The airfare is economical at $150 USD.\n\n5.
	 * **Las Vegas, NV:** The weather is gloomy at 40°F, much like San Diego and Myrtle
	 * Beach, and the airfare is $150 USD.\n\nFor pleasant weather suitable for playing
	 * golf, **Phoenix, AZ** and **Orlando, FL** are your best bets right now, with
	 * Phoenix being slightly warmer. However, Phoenix is also the most expensive in terms
	 * of airfare. If you're looking for a balance between economical travel and good
	 * weather, **Orlando, FL** might be the better choice, as it has similar weather to
	 * Phoenix but the airfare is slightly less." , "properties" : { "role" : "ASSISTANT"
	 * }, "messageType" : "ASSISTANT", "choiceMetadata" : { "contentFilterMetadata" :
	 * null, "finishReason" : "STOP" } }
	 */
	@Test
	void functionCalling() throws Exception {

		// Querries that potentially use function calling:
		// String query = "I travel to Austin, TX on weekends but live in Pittsburgh, PA.
		// I can't stand the heat. Should I be travelling this weekend?";

		// String query = "Where should I travel this weekend to enjoy a game of golf?";
		// String query = "Should I be travelling to Austin TX for a game of golf this
		// weekend?";

		// Does not use function calling:
		// String query = "What is the capital of USA?";

		Message systemMessage = new SystemMessage(
				"I live in the US, so use the units of temperature and currency that are applicable for Americans.");

		String query = "I love playing golf when it's not too cold or hot. I live in Pittsburgh, PA and can travel anywhere in the country to play. Where should I go "
				+ "where it is pleasant and economical based on airfares?";

		UserMessage userMessage = new UserMessage(query);
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

		String weatherParametersMetaData = MyFunctionService.getWeatherParametersMetaData();
		String airfareParametersMetaData = MyFunctionService.getAirfareParametersMetaData();

		List<OpenAiApi.FunctionTool> toolList = List.of(
				new OpenAiApi.FunctionTool(new OpenAiApi.FunctionTool.Function("Gets the weather in a given location",
						"getWeatherForLocation", weatherParametersMetaData)),
				new OpenAiApi.FunctionTool(new OpenAiApi.FunctionTool.Function(
						"Gets the airfare from Pittsburgh, PA, for a given destination", "getAirfareForLocation",
						airfareParametersMetaData)));

		MyFunctionService myFunctionService = new MyFunctionService();
		ChatResponse chatResponse = openAiChatClientSupportingFunctionCalling.call(prompt, toolList, myFunctionService,
				"gpt-4-1106-preview"); // Function calling works better
		// with gpt-4 variants

		// ChatResponse chatResponse =
		// openAiChatClientSupportingFunctionCalling.generate(prompt);

		ObjectMapper objectMapper = new ObjectMapper();
		String finalAnswer = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(chatResponse.getResult());

		logger.debug("Final Answer: " + finalAnswer);
	}

}

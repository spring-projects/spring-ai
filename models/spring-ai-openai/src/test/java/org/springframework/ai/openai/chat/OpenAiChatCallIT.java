package org.springframework.ai.openai.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.call.ChatCall;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@SpringBootTest(classes = OpenAiChatCallIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiChatCallIT {

	private ChatClient chatClient;

	private ChatCall chatCall;

	@Autowired
	public OpenAiChatCallIT(ChatClient chatClient, ChatCall chatCall) {
		this.chatClient = chatClient;
		this.chatCall = chatCall;
	}

	@Test
	void userMessage() {
		// The creation of ChatCall would usually be in a @Bean
		ChatCall chatCall = ChatCall.builder(chatClient)
			.withUserString("Tell me a {adjective} joke about {topic}")
			.build();

		String joke = chatCall.execute(Map.of("adjective", "silly", "topic", "cows"));
		System.out.println(joke);
	}

	@Test
	void testUserAndSystemMessage() {
		// @Bean begin
		String userString = """
				Tell me about three famous {occupation} and what they did.
				Write at least three sentences for each person.
				""";
		String systemString = """
				 You are a helpful AI assistant.
				 You are an AI assistant that helps people find information.
				 Your name is {name}
				 You should reply to the user's request with your name and also in the style of a {voice}.
				""";

		ChatCall chatCall = ChatCall.builder(chatClient)
			.withUserString(userString)
			.withSystemString(systemString)
			.withSystemMap(Map.of("name", "Rick", "voice", "Rick Sanchez"))
			.build();

		System.out.println("Using default temperature");
		Map<String, Object> userMap = Map.of("occupation", "scientists");
		String answer = chatCall.execute(userMap);
		System.out.println(answer);

		ChatOptions chatOptions = ChatOptionsBuilder.builder().withTemperature(1.0f).build();
		chatCall = ChatCall.builder(chatClient)
			.withUserString(userString)
			// .withMedia(List.of(mediaObject)) TODO
			// .withFunctionName() TODO
			.withSystemString(systemString)
			.withChatOptions(chatOptions)
			.build();

		System.out.println("Using temperature 1.0");
		answer = chatCall.execute(userMap, Map.of("name", "Rick", "voice", "Rick Sanchez"));
		System.out.println(answer);
	}

	@Test
	void objectMapping() {
		String userString = "Generate the filmography for the actor {actor}";

		ChatCall chatCall = ChatCall.builder(chatClient).withUserString(userString).build();

		ActorsFilms actorsFilms = chatCall.execute(ActorsFilms.class, Map.of("actor", "Tom Hanks"));
		System.out.println(actorsFilms);
	}

	public record ActorsFilms(String actor, List<String> movies) {

	}

	@Test
	void simpleChain() {
		Function<String, String> composedFunction = generateSynopsis.andThen(generateReview);
		String result = composedFunction.apply("Tragedy at sunset on the beach");
		System.out.println(result);
	}

	private Function<String, String> generateSynopsis = title -> {
		String synopsisInput = """
				You are a playwright. Given the title of play, it is your job to write a synopsis for that title.

				Title: {title}
				Playwright: This is a synopsis for the above play:
				""";
		return this.chatCall.execute(synopsisInput, Map.of("title", title));
	};

	private Function<String, String> generateReview = synopsis -> {
		String synopsisInput = """
				You are a play critic from the New York Times. Given the synopsis of play, it is your job to write a review for that play.

				Play Synopsis:
				{synopsis}
				Review from a New York Times play critic of the above play:""";

		return this.chatCall.execute(synopsisInput, Map.of("synopsis", synopsis));
	};

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public ChatClient openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatClient(openAiApi);
		}

		@Bean
		public ChatCall chatCall(ChatClient chatClient) {
			return ChatCall.builder(chatClient).build();
		}

	}

}
package org.springframework.ai.qwen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.ai.qwen.api.QwenApi;
import org.springframework.ai.qwen.api.QwenModel;
import org.springframework.ai.qwen.api.QwenSearchInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = QwenChatModelIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(QwenChatModelIT.class);

	@Autowired
	private QwenChatModel chatModel;

	@Test
	void roleTest() {
		Message systemMessage = new SystemPromptTemplate("""
				You are a helpful AI assistant. Your name is {name}.
				You are an AI assistant that helps people find information.
				Your name is {name}
				You should reply to the user's request with your name and also in the style of a {voice}.
				""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

		UserMessage userMessage = new UserMessage("Generate the names of 5 famous pirates.");

		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));
		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).contains("Blackbeard");
	}

	@Test
	void messageHistoryTest() {

		Message systemMessage = new SystemPromptTemplate("""
				You are a helpful AI assistant. Your name is {name}.
				You are an AI assistant that helps people find information.
				Your name is {name}
				You should reply to the user's request with your name and also in the style of a {voice}.
				""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");

		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

		ChatResponse response = this.chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard");

		var promptWithMessageHistory = new Prompt(List.of(new UserMessage("Dummy"), response.getResult().getOutput(),
				new UserMessage("Repeat the last assistant message.")));
		response = this.chatModel.call(promptWithMessageHistory);

		System.out.println(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Blackbeard");
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
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "ice cream flavors", "format", format));
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
				Provide me a List of {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format", format));
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
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilms actorsFilms = outputConverter.convert(generation.getOutput().getText());
		assertThat(actorsFilms.actor()).isNotNull();
	}

	@Test
	void beanOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generation.getOutput().getText());
		logger.info(actorsFilms.toString());
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void beanStreamOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> converter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = converter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		// @formatter:off
        String generationTextFromStream = this.chatModel.stream(prompt)
                .collectList()
                .block()
                .stream()
                .map(ChatResponse::getResults)
                .flatMap(List::stream)
                .map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
        // @formatter:on

		ActorsFilmsRecord actorsFilms = converter.convert(generationTextFromStream);
		logger.info(actorsFilms.toString());
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void multiModalityImageUrl() throws IOException {
		URL url = new URL("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png");

		// @formatter:off
        String response = ChatClient.create(this.chatModel).prompt()
                .options(QwenChatOptions.builder().model(QwenModel.QWEN_VL_MAX.getName()).build())
                .user(u -> u.text("Explain what do you see on this picture?").media(MimeTypeUtils.IMAGE_PNG, url))
                .call()
                .content();
        // @formatter:on

		logger.info(response);
		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@Test
	void multiModalityImageResource() {
		Resource resource = new ClassPathResource("multimodal.test.png");

		// @formatter:off
        String response = ChatClient.create(this.chatModel).prompt()
                .options(QwenChatOptions.builder().model(QwenModel.QWEN_VL_MAX.getName()).build())
                .user(u -> u.text("Explain what do you see on this picture?").media(MimeTypeUtils.IMAGE_PNG, resource))
                .call()
                .content();
        // @formatter:on

		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@Test
	void answerAfterSearch() {
		// @formatter:off
        QwenChatOptions options = QwenChatOptions.builder()
                .enableSearch(true)
                .searchOptions(QwenChatOptions.SearchOptions.builder()
                        .citationFormat("[<number>]")
                        .enableCitation(true)
                        .enableSource(true)
                        .forcedSearch(true)
                        .searchStrategy("standard")
                        .build())
                .build();
        // @formatter:on

		Prompt prompt = new Prompt("What is the weather of Beijing?", options);

		ChatResponse response = chatModel.call(prompt);
		System.out.println(response.getResult().getOutput().getText());
		ChatResponseMetadata metadata = response.getMetadata();
		QwenSearchInfo searchInfo = metadata.get("searchInfo");
		assertThat(searchInfo).isNotNull();
		assertThat(searchInfo.searchResults()).isNotEmpty();
	}

	@Test
	void translateMessage() {
		// @formatter:off
        QwenChatOptions options = QwenChatOptions.builder()
                .model(QwenModel.QWEN_MT_PLUS.getName())
                .translationOptions(QwenChatOptions.TranslationOptions.builder()
                        .sourceLang("English")
                        .targetLang("Chinese")
                        .terms(singletonList(QwenChatOptions.TranslationOptionTerm.builder()
                                .source("memory")
                                .target("内存")
                                .build()))
                        .domains("Translate into this IT domain style.")
                        .build())
                .build();
        // @formatter:on

		Prompt prompt = new Prompt("my memory", options);

		ChatResponse response = chatModel.call(prompt);
		String chineseContent = response.getResult().getOutput().getText().trim();
		System.out.println(chineseContent);
		assertThat(chineseContent).isEqualTo("我的内存");
	}

	record ActorsFilms(String actor, List<String> movies) {
	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public QwenApi qwenApi() {
			return QwenApi.builder().apiKey(System.getenv("DASHSCOPE_API_KEY")).build();
		}

		@Bean
		public QwenChatModel qwenChatModel(QwenApi qwenApi) {
			return QwenChatModel.builder().qwenApi(qwenApi).build();
		}

	}

}

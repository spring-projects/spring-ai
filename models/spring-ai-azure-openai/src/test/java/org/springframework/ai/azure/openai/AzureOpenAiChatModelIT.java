/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.azure.openai;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.HttpLogOptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RequiresAzureCredentials
class AzureOpenAiChatModelIT {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiChatModelIT.class);

	@Autowired
	private AzureOpenAiChatModel chatModel;

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
	void testMessageHistory() {

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
	void testStreaming() {
		String prompt = """
				Provide a list of planets in our solar system
				""";

		final var counter = new AtomicInteger();
		String content = this.chatModel.stream(prompt)
			.doOnEach(listSignal -> counter.getAndIncrement())
			.collectList()
			.block()
			.stream()
			.collect(Collectors.joining());
		logger.info("Response: {}", content);

		assertThat(counter.get()).withFailMessage("More than 8 chunks because there are 8 planets").isGreaterThan(8);

		assertThat(content).contains("Earth", "Mars", "Jupiter");
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
				{format}
				""";
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
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
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generation.getOutput().getText());
		logger.info("" + actorsFilms);
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
		PromptTemplate promptTemplate = PromptTemplate.builder()
			.template(template)
			.variables(Map.of("format", format))
			.build();
		Prompt prompt = new Prompt(promptTemplate.createMessage());

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

		ActorsFilmsRecord actorsFilms = converter.convert(generationTextFromStream);
		logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void multiModalityImageUrl() throws IOException {

		// TODO: add url method that wraps the checked exception.
		URL url = new URL("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png");

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.options(AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").build())
				.user(u -> u.text("Explain what do you see on this picture?").media(MimeTypeUtils.IMAGE_PNG, url))
				.call()
				.content();
		// @formatter:on

		logger.info(response);
		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@Test
	void multiModalityImageResource() {

		Resource resource = new ClassPathResource("multimodality/multimodal.test.png");

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.options(AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").build())
				.user(u -> u.text("Explain what do you see on this picture?").media(MimeTypeUtils.IMAGE_PNG, resource))
				.call()
				.content();
		// @formatter:on

		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@Test
	void testMaxCompletionTokensBlocking() {
		// Test with a very low maxCompletionTokens to verify it limits the response
		String prompt = """
				Write a detailed essay about the history of artificial intelligence,
				including its origins, major milestones, key researchers, current applications,
				and future prospects. Make it comprehensive and detailed.
				""";

		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(AzureOpenAiChatOptions.builder()
						.deploymentName("gpt-4o")
						.maxCompletionTokens(50)
						.build())
				.user(prompt)
				.call()
				.chatResponse();
		// @formatter:on

		String content = response.getResult().getOutput().getText();
		logger.info("Response with maxCompletionTokens=50: {}", content);

		// Verify the response is limited and not empty
		assertThat(content).isNotEmpty();

		// The response should be relatively short due to the 50 token limit
		// We can't test exact token count but can verify it's significantly shorter than
		// unlimited
		assertThat(content.length()).isLessThan(500); // Rough approximation for 50 tokens

		// Verify usage metadata if available
		if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
			var usage = response.getMetadata().getUsage();
			logger.info("Token usage - Total: {}, Prompt: {}, Completion: {}", usage.getTotalTokens(),
					usage.getPromptTokens(), usage.getCompletionTokens());

			// The completion tokens should be limited by maxCompletionTokens
			if (usage.getCompletionTokens() != null) {
				assertThat(usage.getCompletionTokens()).isLessThanOrEqualTo(50);
			}
		}
	}

	@Test
	void testMaxCompletionTokensStreaming() {
		String prompt = """
				Write a detailed explanation of machine learning algorithms,
				covering supervised learning, unsupervised learning, and reinforcement learning.
				Include examples and applications for each type.
				""";

		// @formatter:off
		String content = ChatClient.create(this.chatModel).prompt()
				.options(AzureOpenAiChatOptions.builder()
						.deploymentName("gpt-4o")
						.maxCompletionTokens(30)
						.build())
				.user(prompt)
				.stream()
				.content()
				.collectList()
				.block()
				.stream()
				.collect(Collectors.joining());
		// @formatter:on

		logger.info("Streaming response with maxCompletionTokens=30: {}", content);

		// Verify the response is limited and not empty
		assertThat(content).isNotEmpty();

		// The response should be very short due to the 30 token limit
		assertThat(content.length()).isLessThan(300); // Rough approximation for 30 tokens
	}

	@Test
	void testMaxCompletionTokensOptionsBuilder() {
		// Test that maxCompletionTokens can be set via builder and is properly retrieved
		AzureOpenAiChatOptions options = AzureOpenAiChatOptions.builder()
			.deploymentName("gpt-4o")
			.maxCompletionTokens(100)
			.temperature(0.7)
			.build();

		assertThat(options.getMaxCompletionTokens()).isEqualTo(100);
		assertThat(options.getDeploymentName()).isEqualTo("gpt-4o");
		assertThat(options.getTemperature()).isEqualTo(0.7);
	}

	@Test
	void testMaxTokensForNonReasoningModels() {
		// Test maxTokens parameter for non-reasoning models (e.g., gpt-4o)
		// maxTokens limits total tokens (input + output)
		String prompt = "Explain quantum computing in simple terms. Please provide a detailed explanation.";

		// @formatter:off
		ChatResponse response = ChatClient.create(this.chatModel).prompt()
				.options(AzureOpenAiChatOptions.builder()
						.deploymentName("gpt-4o")
						.maxTokens(100)  // Total tokens limit for non-reasoning models
						.build())
				.user(prompt)
				.call()
				.chatResponse();
		// @formatter:on

		String content = response.getResult().getOutput().getText();
		logger.info("Response with maxTokens=100: {}", content);

		assertThat(content).isNotEmpty();

		// Verify usage metadata if available
		if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
			var usage = response.getMetadata().getUsage();
			logger.info("Token usage - Total: {}, Prompt: {}, Completion: {}", usage.getTotalTokens(),
					usage.getPromptTokens(), usage.getCompletionTokens());

			// Total tokens should be close to maxTokens (Azure may slightly exceed the
			// limit)
			if (usage.getTotalTokens() != null) {
				assertThat(usage.getTotalTokens()).isLessThanOrEqualTo(150); // Allow some
																				// tolerance
			}
		}
	}

	@Test
	void testModelInStreamingResponse() {
		String prompt = "List three colors of the rainbow.";

		// @formatter:off
		Flux<ChatResponse> responseFlux = ChatClient.create(this.chatModel).prompt()
				.options(AzureOpenAiChatOptions.builder()
						.deploymentName("gpt-4o")
						.build())
				.user(prompt)
				.stream()
				.chatResponse();
		// @formatter:on

		List<ChatResponse> responses = responseFlux.collectList().block();

		assertThat(responses).isNotEmpty();

		ChatResponse lastResponse = responses.get(responses.size() - 1);

		// Verify that the final merged response has model metadata
		assertThat(lastResponse.getMetadata()).as("Last response should have metadata").isNotNull();
		assertThat(lastResponse.getMetadata().getModel()).as("Last response metadata should contain model").isNotNull();

		String model = lastResponse.getMetadata().getModel();
		logger.info("Final merged response model: {}", model);
		assertThat(model).isNotEmpty();
		// Azure OpenAI models typically contain "gpt" in their name
		assertThat(model).containsIgnoringCase("gpt");

		String content = responses.stream()
			.flatMap(r -> r.getResults().stream())
			.map(Generation::getOutput)
			.map(AssistantMessage::getText)
			.filter(Objects::nonNull)
			.collect(Collectors.joining());

		assertThat(content).isNotEmpty();
		logger.info("Generated content: {}", content);
	}

	record ActorsFilms(String actor, List<String> movies) {

	}

	record ActorsFilmsRecord(String actor, List<String> movies) {

	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OpenAIClientBuilder openAIClientBuilder() {
			return new OpenAIClientBuilder().credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
				.endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
				.serviceVersion(OpenAIServiceVersion.V2024_02_15_PREVIEW)
				.httpLogOptions(new HttpLogOptions()
					.setLogLevel(com.azure.core.http.policy.HttpLogDetailLevel.BODY_AND_HEADERS));
		}

		@Bean
		public AzureOpenAiChatModel azureOpenAiChatModel(OpenAIClientBuilder openAIClientBuilder) {
			return AzureOpenAiChatModel.builder()
				.openAIClientBuilder(openAIClientBuilder)
				.defaultOptions(AzureOpenAiChatOptions.builder().deploymentName("gpt-4o").build())
				.build();
		}

	}

}

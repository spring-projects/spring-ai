/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.azure.openai.autoconfigure;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.implementation.OpenAIClientImpl;
import com.azure.core.http.HttpHeader;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpPipeline;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Piotr Olaszewski
 * @author Soby Chacko
 * @author Manuel Andreo Garcia
 * @author Issam El-atif
 * @since 0.8.0
 */
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+")
class AzureOpenAiAutoConfigurationIT {

	private static String CHAT_MODEL_NAME = "gpt-4o";

	private static String EMBEDDING_MODEL_NAME = "text-embedding-ada-002";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
			"spring.ai.azure.openai.api-key=" + System.getenv("AZURE_OPENAI_API_KEY"),
			"spring.ai.azure.openai.endpoint=" + System.getenv("AZURE_OPENAI_ENDPOINT"),

			"spring.ai.azure.openai.chat.options.deployment-name=" + CHAT_MODEL_NAME,
			"spring.ai.azure.openai.chat.options.temperature=0.8",
			"spring.ai.azure.openai.chat.options.maxTokens=123",

			"spring.ai.azure.openai.embedding.options.deployment-name=" + EMBEDDING_MODEL_NAME,
			"spring.ai.azure.openai.audio.transcription.options.deployment-name=" + System.getenv("AZURE_OPENAI_TRANSCRIPTION_DEPLOYMENT_NAME")
			// @formatter:on
	);

	private final Message systemMessage = new SystemPromptTemplate("""
			You are a helpful AI assistant. Your name is {name}.
			You are an AI assistant that helps people find information.
			Your name is {name}
			You should reply to the user's request with your name and also in the style of a {voice}.
			""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

	private final UserMessage userMessage = new UserMessage(
			"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");

	@Test
	void chatCompletion() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.run(context -> {
				AzureOpenAiChatModel chatModel = context.getBean(AzureOpenAiChatModel.class);
				ChatResponse response = chatModel.call(new Prompt(List.of(this.userMessage, this.systemMessage)));
				assertThat(response.getResult().getOutput().getText()).contains("Blackbeard");
			});
	}

	@Test
	void httpRequestContainsUserAgentAndCustomHeaders() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.azure.openai.custom-headers.foo=bar",
					"spring.ai.azure.openai.custom-headers.fizz=buzz")
			.run(context -> {
				OpenAIClientBuilder openAIClientBuilder = context.getBean(OpenAIClientBuilder.class);
				OpenAIClient openAIClient = openAIClientBuilder.buildClient();
				Field serviceClientField = ReflectionUtils.findField(OpenAIClient.class, "serviceClient");
				assertThat(serviceClientField).isNotNull();
				ReflectionUtils.makeAccessible(serviceClientField);
				OpenAIClientImpl oaci = (OpenAIClientImpl) ReflectionUtils.getField(serviceClientField, openAIClient);
				assertThat(oaci).isNotNull();
				HttpPipeline httpPipeline = oaci.getHttpPipeline();
				HttpResponse httpResponse = httpPipeline
					.send(new HttpRequest(HttpMethod.POST, new URI(System.getenv("AZURE_OPENAI_ENDPOINT")).toURL()))
					.block();
				assertThat(httpResponse).isNotNull();
				HttpHeader httpHeader = httpResponse.getRequest().getHeaders().get(HttpHeaderName.USER_AGENT);
				assertThat(httpHeader.getValue().startsWith("spring-ai azsdk-java-azure-ai-openai/")).isTrue();
				HttpHeader customHeader1 = httpResponse.getRequest().getHeaders().get("foo");
				assertThat(customHeader1.getValue()).isEqualTo("bar");
				HttpHeader customHeader2 = httpResponse.getRequest().getHeaders().get("fizz");
				assertThat(customHeader2.getValue()).isEqualTo("buzz");
			});
	}

	@Test
	void chatCompletionStreaming() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.run(context -> {

				AzureOpenAiChatModel chatModel = context.getBean(AzureOpenAiChatModel.class);

				Flux<ChatResponse> response = chatModel
					.stream(new Prompt(List.of(this.userMessage, this.systemMessage)));

				List<ChatResponse> responses = response.collectList().block();
				assertThat(responses.size()).isGreaterThan(10);

				String stitchedResponseContent = responses.stream()
					.map(ChatResponse::getResults)
					.flatMap(List::stream)
					.map(Generation::getOutput)
					.map(AssistantMessage::getText)
					.collect(Collectors.joining());

				assertThat(stitchedResponseContent).contains("Blackbeard");
			});
	}

	@Test
	void embedding() {
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				AzureOpenAiEmbeddingModel embeddingModel = context.getBean(AzureOpenAiEmbeddingModel.class);

				EmbeddingResponse embeddingResponse = embeddingModel
					.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
				assertThat(embeddingResponse.getResults()).hasSize(2);
				assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
				assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
				assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
				assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

				assertThat(embeddingModel.dimensions()).isEqualTo(1536);
			});

	}

	@Test
	@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_TRANSCRIPTION_DEPLOYMENT_NAME", matches = ".+")
	void transcribe() {
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				AzureOpenAiAudioTranscriptionModel transcriptionModel = context
					.getBean(AzureOpenAiAudioTranscriptionModel.class);
				Resource audioFile = new ClassPathResource("/speech/jfk.flac");
				String response = transcriptionModel.call(audioFile);
				assertThat(response).isEqualTo(
						"And so my fellow Americans, ask not what your country can do for you, ask what you can do for your country.");
			});
	}

	@Test
	void chatActivation() {

		// Disable the chat auto-configuration.
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isEmpty();
			});

		// The chat auto-configuration is enabled by default.
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiChatProperties.class)).isNotEmpty();
			});

		// Explicitly enable the chat auto-configuration.
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.chat=azure-openai")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiChatModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiChatProperties.class)).isNotEmpty();
			});
	}

	@Test
	void embeddingActivation() {

		// Disable the embedding auto-configuration.
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingProperties.class)).isEmpty();
			});

		// The embedding auto-configuration is enabled by default.
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingProperties.class)).isNotEmpty();
			});

		// Explicitly enable the embedding auto-configuration.
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding=azure-openai")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingModel.class)).isNotEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiEmbeddingProperties.class)).isNotEmpty();
			});
	}

	@Test
	void audioTranscriptionActivation() {

		// Disable the transcription auto-configuration.
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiAudioTranscriptionAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.transcription=none")
			.run(context -> {
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isEmpty();
				assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionProperties.class)).isEmpty();
			});

		// The transcription auto-configuration is enabled by default.
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isNotEmpty());

		// Explicitly enable the transcription auto-configuration.
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiAudioTranscriptionAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.audio.transcription=azure-openai")
			.run(context -> assertThat(context.getBeansOfType(AzureOpenAiAudioTranscriptionModel.class)).isNotEmpty());
	}

	@Test
	void openAIClientBuilderCustomizer() {
		AtomicBoolean firstCustomizationApplied = new AtomicBoolean(false);
		AtomicBoolean secondCustomizationApplied = new AtomicBoolean(false);
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(AzureOpenAiChatAutoConfiguration.class))
			.withBean("first", AzureOpenAIClientBuilderCustomizer.class,
					() -> clientBuilder -> firstCustomizationApplied.set(true))
			.withBean("second", AzureOpenAIClientBuilderCustomizer.class,
					() -> clientBuilder -> secondCustomizationApplied.set(true))
			.run(context -> {
				context.getBean(OpenAIClientBuilder.class);
				assertThat(firstCustomizationApplied.get()).isTrue();
				assertThat(secondCustomizationApplied.get()).isTrue();
			});
	}

}

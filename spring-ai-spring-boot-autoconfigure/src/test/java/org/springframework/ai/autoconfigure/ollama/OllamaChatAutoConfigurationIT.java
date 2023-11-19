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

package org.springframework.ai.autoconfigure.ollama;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Image;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @since 0.8.0
 */
@Testcontainers
public class OllamaChatAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OllamaChatAutoConfigurationIT.class);

	private static String MODEL_NAME = "mistral";

	private static final String OLLAMA_WITH_MODEL = "%s-%s".formatted(MODEL_NAME, OllamaImage.IMAGE);

	private static final OllamaContainer ollamaContainer;

	static {
		ollamaContainer = new OllamaContainer(OllamaDockerImageName.image());
		ollamaContainer.start();
		createImage(ollamaContainer, OLLAMA_WITH_MODEL);
	}

	static String baseUrl;

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the '" + MODEL_NAME + " ' generative ... would take several minutes ...");
		ollamaContainer.execInContainer("ollama", "pull", MODEL_NAME);
		logger.info(MODEL_NAME + " pulling competed!");

		baseUrl = "http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434);
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withPropertyValues(
	// @formatter:off
				"spring.ai.ollama.baseUrl=" + baseUrl,
				"spring.ai.ollama.chat.options.model=" + MODEL_NAME,
				"spring.ai.ollama.chat.options.temperature=0.5",
				"spring.ai.ollama.chat.options.topK=10")
				// @formatter:on
		.withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class, OllamaAutoConfiguration.class));

	private final Message systemMessage = new SystemPromptTemplate("""
			You are a helpful AI assistant. Your name is {name}.
			You are an AI assistant that helps people find information.
			Your name is {name}
			You should reply to the user's request with your name and also in the style of a {voice}.
			""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

	private final UserMessage userMessage = new UserMessage(
			"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");

	@Test
	public void chatCompletion() {
		contextRunner.run(context -> {
			OllamaChatClient chatClient = context.getBean(OllamaChatClient.class);
			ChatResponse response = chatClient.call(new Prompt(List.of(userMessage, systemMessage)));
			assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");
		});
	}

	@Test
	public void chatCompletionStreaming() {
		contextRunner.run(context -> {

			OllamaChatClient chatClient = context.getBean(OllamaChatClient.class);

			Flux<ChatResponse> response = chatClient.stream(new Prompt(List.of(userMessage, systemMessage)));

			List<ChatResponse> responses = response.collectList().block();
			assertThat(responses.size()).isGreaterThan(1);

			String stitchedResponseContent = responses.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getContent)
				.collect(Collectors.joining());

			assertThat(stitchedResponseContent).contains("Blackbeard");
		});
	}

	@Test
	void chatActivation() {
		contextRunner.withPropertyValues("spring.ai.ollama.chat.enabled=false").run(context -> {
			assertThat(context.getBeansOfType(OllamaChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OllamaChatClient.class)).isEmpty();
		});

		contextRunner.run(context -> {
			assertThat(context.getBeansOfType(OllamaChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OllamaChatClient.class)).isNotEmpty();
		});

		contextRunner.withPropertyValues("spring.ai.ollama.chat.enabled=true").run(context -> {
			assertThat(context.getBeansOfType(OllamaChatProperties.class)).isNotEmpty();
			assertThat(context.getBeansOfType(OllamaChatClient.class)).isNotEmpty();
		});
	}

	static class OllamaContainer extends GenericContainer<OllamaContainer> {

		private final DockerImageName dockerImageName;

		OllamaContainer(DockerImageName image) {
			super(image);
			this.dockerImageName = image;
			withExposedPorts(11434);
			withImagePullPolicy(dockerImageName -> !dockerImageName.getUnversionedPart().startsWith(MODEL_NAME));
		}

		@Override
		protected void containerIsStarted(InspectContainerResponse containerInfo) {
			if (!this.dockerImageName.getVersionPart().endsWith(MODEL_NAME)) {
				try {
					execInContainer("ollama", "pull", MODEL_NAME);
				}
				catch (IOException | InterruptedException e) {
					throw new RuntimeException("Error pulling orca-mini model", e);
				}
			}
		}

	}

	static void createImage(GenericContainer<?> container, String localImageName) {
		DockerImageName dockerImageName = DockerImageName.parse(container.getDockerImageName());
		if (!dockerImageName.equals(DockerImageName.parse(localImageName))) {
			DockerClient dockerClient = DockerClientFactory.instance().client();
			List<Image> images = dockerClient.listImagesCmd().withReferenceFilter(localImageName).exec();
			if (images.isEmpty()) {
				DockerImageName imageModel = DockerImageName.parse(localImageName);
				dockerClient.commitCmd(container.getContainerId())
					.withRepository(imageModel.getUnversionedPart())
					.withLabels(Collections.singletonMap("org.testcontainers.sessionId", ""))
					.withTag(imageModel.getVersionPart())
					.exec();
			}
		}
	}

	static class OllamaDockerImageName {

		private final String baseImage;

		private final String localImageName;

		OllamaDockerImageName(String baseImage, String localImageName) {
			this.baseImage = baseImage;
			this.localImageName = localImageName;
		}

		static DockerImageName image() {
			return new OllamaDockerImageName(OllamaImage.IMAGE, OLLAMA_WITH_MODEL).resolve();
		}

		private DockerImageName resolve() {
			var dockerImageName = DockerImageName.parse(this.baseImage);
			var dockerClient = DockerClientFactory.instance().client();
			var images = dockerClient.listImagesCmd().withReferenceFilter(this.localImageName).exec();
			if (images.isEmpty()) {
				return dockerImageName;
			}
			return DockerImageName.parse(this.localImageName);
		}

	}

}

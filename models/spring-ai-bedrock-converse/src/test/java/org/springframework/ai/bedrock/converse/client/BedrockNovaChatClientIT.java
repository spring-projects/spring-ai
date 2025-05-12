/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.bedrock.converse.client;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.converse.BedrockProxyChatModel;
import org.springframework.ai.bedrock.converse.RequiresAwsCredentials;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Christian Tzolov
 */
// @Disabled
@SpringBootTest(classes = BedrockNovaChatClientIT.Config.class)
@RequiresAwsCredentials
public class BedrockNovaChatClientIT {

	private static final Logger logger = LoggerFactory.getLogger(BedrockNovaChatClientIT.class);

	@Autowired
	ChatModel chatModel;

	@Test
	void pdfMultiModalityTest() throws IOException {

		String response = ChatClient.create(this.chatModel)
			.prompt()
			.user(u -> u.text(
					"You are a very professional document summarization specialist. Please summarize the given document.")
				.media(Media.Format.DOC_PDF, new ClassPathResource("/spring-ai-reference-overview.pdf")))
			.call()
			.content();

		logger.info(response);
		assertThat(response).containsAnyOf("Spring AI", "portable API");
	}

	@Test
	void imageMultiModalityTest() throws IOException {

		String response = ChatClient.create(this.chatModel)
			.prompt()
			.user(u -> u.text("Explain what do you see on this picture?")
				.media(Media.Format.IMAGE_PNG, new ClassPathResource("/test.png")))
			.call()
			.content();

		logger.info(response);
		assertThat(response).containsAnyOf("bananas", "apple", "bowl", "basket", "fruit stand");
	}

	@Test
	void videoMultiModalityTest() throws IOException {
		// Define sets of semantically similar words for different concepts
		Set<String> youngDescriptors = Set.of("baby", "small", "young", "little", "tiny", "juvenile", "newborn",
				"infant", "hatchling", "downy", "fluffy");

		Set<String> birdDescriptors = Set.of("chick", "chicks", "chicken", "chickens", "bird", "birds", "poultry",
				"hatchling", "hatchlings");

		String response = ChatClient.create(this.chatModel)
			.prompt()
			.user(u -> u.text("Explain what do you see in this video?")
				.media(Media.Format.VIDEO_MP4, new ClassPathResource("/test.video.mp4")))
			.call()
			.content();

		logger.info(response);

		// Convert response to lowercase for case-insensitive matching
		String lowerResponse = response.toLowerCase();

		// Test for presence of young/small descriptors
		boolean hasYoungDescriptor = youngDescriptors.stream()
			.anyMatch(word -> lowerResponse.contains(word.toLowerCase()));

		// Test for presence of bird/chicken descriptors
		boolean hasBirdDescriptor = birdDescriptors.stream()
			.anyMatch(word -> lowerResponse.contains(word.toLowerCase()));

		// Additional semantic checks
		boolean describesMovement = lowerResponse.contains("mov") || lowerResponse.contains("walk")
				|| lowerResponse.contains("peck");

		boolean describesAppearance = lowerResponse.contains("feather") || lowerResponse.contains("fluff")
				|| lowerResponse.contains("color");

		// Comprehensive assertions with detailed failure messages
		assertAll("Video content analysis",
				() -> assertTrue(hasYoungDescriptor,
						String.format("Response should contain at least one young descriptor. Response: '%s'",
								response)),
				() -> assertTrue(hasBirdDescriptor,
						String.format("Response should contain at least one bird descriptor. Response: '%s'",
								response)),
				() -> assertTrue(describesMovement || describesAppearance,
						String.format("Response should describe either movement or appearance. Response: '%s'",
								response)),
				() -> assertTrue(response.length() > 50, "Response should be sufficiently detailed (>50 characters)"));
	}

	@Test
	void functionCallTest() {

		// @formatter:off
		String response = ChatClient.create(this.chatModel).prompt()
				.user("What's the weather like in San Francisco, Tokyo, and Paris?  Use Celsius.")
				.toolCallbacks(FunctionToolCallback.builder("getCurrentWeather", (WeatherRequest request) -> {
						if (request.location().contains("Paris")) {
							return new WeatherResponse(15, request.unit());
						}
						else if (request.location().contains("Tokyo")) {
							return new WeatherResponse(10, request.unit());
						}
						else if (request.location().contains("San Francisco")) {
							return new WeatherResponse(30, request.unit());
						}
						throw new IllegalArgumentException("Unknown location: " + request.location());
					})
					.description("Get the weather for a city in Celsius")
					.inputType(WeatherRequest.class)
					.build())
				.call()
				.content();
		// @formatter:on

		logger.info("Response: {}", response);

		assertThat(response).contains("30", "10", "15");
	}

	public record WeatherRequest(String location, String unit) {
	}

	public record WeatherResponse(int temp, String unit) {
	}

	// https://github.com/spring-projects/spring-ai/issues/1878
	@Test
	void toolAnnotationWeatherForecast() {

		ChatClient chatClient = ChatClient.builder(this.chatModel).build();

		String response = chatClient.prompt()
			.tools(new DummyWeatherForcastTools())
			.user("Get current weather in Amsterdam")
			.call()
			.content();

		assertThat(response).isNotEmpty();
		assertThat(response).contains("20 degrees");
	}

	@Test
	void toolAnnotationWeatherForecastStreaming() {

		ChatClient chatClient = ChatClient.builder(this.chatModel).build();

		Flux<ChatResponse> responses = chatClient.prompt()
			.tools(new DummyWeatherForcastTools())
			.user("Get current weather in Amsterdam")
			.stream()
			.chatResponse();

		String content = responses.collectList()
			.block()
			.stream()
			.filter(cr -> cr.getResult() != null)
			.map(cr -> cr.getResult().getOutput().getText())
			.collect(Collectors.joining());

		assertThat(content).contains("20 degrees");
	}

	public static class DummyWeatherForcastTools {

		@Tool(description = "Get the current weather forcast in Amsterdam")
		String getCurrentDateTime() {
			return "Weahter is hot and sunny wiht a temperature of 20 degrees";
		}

	}

	// https://github.com/spring-projects/spring-ai/issues/1878
	@Test
	void supplierBasedToolCalling() {

		ChatClient chatClient = ChatClient.builder(this.chatModel).build();

		WeatherService.Response response = chatClient.prompt()
			.toolCallbacks(FunctionToolCallback.builder("weather", new WeatherService())
				.description("Get the current weather")
				.inputType(Void.class)
				.build())
			.user("Get current weather in Amsterdam")
			.call()
			.entity(WeatherService.Response.class);

		assertThat(response).isNotNull();
		assertThat(response.temp()).isEqualTo(30.0);
	}

	@Test
	void supplierBasedToolCallingStreaming() {

		ChatClient chatClient = ChatClient.builder(this.chatModel).build();

		Flux<ChatResponse> responses = chatClient.prompt()
			.toolCallbacks(FunctionToolCallback.builder("weather", new WeatherService())
				.description("Get the current weather")
				.inputType(Void.class)
				.build())
			.user("Get current weather in Amsterdam")
			.stream()
			.chatResponse();

		String content = responses.collectList()
			.block()
			.stream()
			.filter(cr -> cr.getResult() != null)
			.map(cr -> cr.getResult().getOutput().getText())
			.collect(Collectors.joining());

		assertThat(content).contains("30.0");
	}

	public static class WeatherService implements Supplier<WeatherService.Response> {

		public record Response(double temp) {
		}

		public Response get() {
			return new Response(30.0);
		}

	}

	@SpringBootConfiguration
	public static class Config {

		@Bean
		public BedrockProxyChatModel bedrockConverseChatModel() {

			String modelId = "amazon.nova-pro-v1:0";

			return BedrockProxyChatModel.builder()
				.credentialsProvider(EnvironmentVariableCredentialsProvider.create())
				.region(Region.US_EAST_1)
				.timeout(Duration.ofSeconds(120))
				.defaultOptions(ToolCallingChatOptions.builder().model(modelId).build())
				.build();
		}

	}

}

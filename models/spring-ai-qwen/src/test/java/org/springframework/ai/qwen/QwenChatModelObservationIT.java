package org.springframework.ai.qwen;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.qwen.api.QwenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = QwenChatModelObservationIT.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class QwenChatModelObservationIT {

	private static final Logger logger = LoggerFactory.getLogger(QwenChatModelObservationIT.class);

	@Autowired
	private TestObservationRegistry observationRegistry;

	@Autowired
	private QwenChatModel chatModel;

	@BeforeEach
	void beforeEach() {
		this.observationRegistry.clear();
	}

	@Test
	void observationForImperativeChatOperation() {

		var options = QwenChatOptions.builder()
			.frequencyPenalty(0.0)
			.maxTokens(2048)
			.presencePenalty(0.0)
			.stopSequences(List.of("this-is-the-end"))
			.temperature(0.7)
			.topP(1.0)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		ChatResponse chatResponse = this.chatModel.call(prompt);
		assertThat(chatResponse.getResult().getOutput().getText()).isNotEmpty();

		ChatResponseMetadata responseMetadata = chatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		validate(responseMetadata, true);
	}

	@Test
	void observationForStreamingChatOperation() {

		var options = QwenChatOptions.builder()
			.frequencyPenalty(0.0)
			.maxTokens(2048)
			.presencePenalty(0.0)
			.stopSequences(List.of("this-is-the-end"))
			.temperature(0.7)
			.topP(1.0)
			.build();

		Prompt prompt = new Prompt("Why does a raven look like a desk?", options);

		Flux<ChatResponse> chatResponseFlux = this.chatModel.stream(prompt);
		List<ChatResponse> responses = chatResponseFlux.collectList().block();
		assertThat(responses).isNotEmpty();
		assertThat(responses).hasSizeGreaterThan(10);

		String aggregatedResponse = responses.subList(0, responses.size() - 1)
			.stream()
			.map(r -> r.getResult().getOutput().getText())
			.collect(Collectors.joining());
		assertThat(aggregatedResponse).isNotEmpty();

		ChatResponse lastChatResponse = responses.get(responses.size() - 1);

		ChatResponseMetadata responseMetadata = lastChatResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		validate(responseMetadata, false);
	}

	private void validate(ChatResponseMetadata responseMetadata, boolean checkModel) {

		TestObservationRegistryAssert.That that = TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultChatModelObservationConvention.DEFAULT_NAME);

		if (checkModel) {
			that.that()
				.hasLowCardinalityKeyValue(
						ChatModelObservationDocumentation.LowCardinalityKeyNames.RESPONSE_MODEL.asString(),
						responseMetadata.getModel());
		}

		that.that()
			.hasLowCardinalityKeyValue(
					ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.CHAT.value())
			.hasLowCardinalityKeyValue(ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.ALIBABA.value())
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_FREQUENCY_PENALTY.asString(),
					"0.0")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_MAX_TOKENS.asString(), "2048")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_PRESENCE_PENALTY.asString(),
					"0.0")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_STOP_SEQUENCES.asString(),
					"[\"this-is-the-end\"]")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TEMPERATURE.asString(), "0.7")
			.doesNotHaveHighCardinalityKeyValueWithKey(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_K.asString())
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOP_P.asString(), "1.0")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_ID.asString(),
					responseMetadata.getId())
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(),
					"[\"stop\"]")
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getPromptTokens()))
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getCompletionTokens()))
			.hasHighCardinalityKeyValue(
					ChatModelObservationDocumentation.HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getTotalTokens()))
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public QwenApi qwenApi() {
			return QwenApi.builder().apiKey(System.getenv("DASHSCOPE_API_KEY")).build();
		}

		@Bean
		public QwenChatModel qwenChatModel(QwenApi qwenApi, TestObservationRegistry observationRegistry) {
			return QwenChatModel.builder().qwenApi(qwenApi).observationRegistry(observationRegistry).build();
		}

	}

}

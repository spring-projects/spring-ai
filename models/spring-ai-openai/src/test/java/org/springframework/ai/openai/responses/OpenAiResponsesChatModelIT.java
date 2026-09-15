/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.openai.responses;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiResponsesTestConfiguration;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiResponsesChatModel}.
 *
 * @author Dimitar Proynov
 */
@SpringBootTest(classes = OpenAiResponsesTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiResponsesChatModelIT {

	/**
	 * A question that reliably buys a reasoning item at {@code high} effort. An easy one
	 * does not: the model answers straight from a text item and the turn carries no
	 * reasoning to assert on, or to replay. The answer is 14:40.
	 */
	private static final String REASONING_QUESTION = """
			A train leaves at 9:00 and takes 90 minutes. A second train leaves 40 minutes
			after the first one arrives and takes 2 hours and 15 minutes. A third leaves
			25 minutes after the second arrives and takes 50 minutes. When does the third
			train arrive? Answer with the time only.""";

	@Autowired
	private OpenAiResponsesChatModel chatModel;

	@Test
	void call() {
		ChatResponse response = this.chatModel.call(new Prompt(List.of(new SystemMessage("Answer with a single word."),
				new UserMessage("What is the capital of France?"))));

		assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("Paris");
		assertThat(response.getMetadata().getId()).startsWith("resp_");
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isPositive();
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("STOP");
	}

	@Test
	void stream() {
		String content = this.chatModel.stream(new Prompt("Count from 1 to 5, digits only."))
			.mapNotNull(response -> response.getResult().getOutput().getText())
			.collectList()
			.block()
			.stream()
			.reduce("", String::concat);

		assertThat(content).contains("5");
	}

	/**
	 * Both the effort and the question have to be enough to make the model think; see
	 * {@link #REASONING_QUESTION}.
	 */
	@Test
	void reasoningIsSurfacedAsAReplayablePart() {
		var options = OpenAiResponsesChatOptions.builder()
			.model("gpt-5.6-luna")
			.reasoningEffort("high")
			.reasoningSummary("auto")
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(REASONING_QUESTION, options));

		AssistantMessage message = response.getResult().getOutput();
		ChatGenerationMetadata metadata = response.getResult().getMetadata();
		// The encrypted blob is always requested and store is always false, so a turn
		// that reasoned carries a replayable reasoning part, not only summary text.
		assertThat(message.getReasoning())
			.as("transcript %s, hosted tool calls %s, reasoningContent '%s'", message.getParts(),
					metadata.get(OpenAiResponsesMetadata.HOSTED_TOOL_CALLS),
					metadata.get(OpenAiResponsesMetadata.REASONING_CONTENT))
			.isNotEmpty()
			.allSatisfy(reasoning -> assertThat(reasoning.replayableTo(OpenAiResponsesMetadata.PROVIDER)).isTrue());

		// The flat metadata key is a view over the parts, whether the model chose
		// to summarize: it must never disagree with them.
		String summaries = message.getReasoning()
			.stream()
			.map(ReasoningPart::summary)
			.filter(Objects::nonNull)
			.collect(Collectors.joining("\n"));
		assertThat(metadata.<String>get(OpenAiResponsesMetadata.REASONING_CONTENT)).isEqualTo(summaries);
	}

	@Test
	void maxOutputTokensTruncatesTheAnswer() {
		var options = OpenAiResponsesChatOptions.builder().model("gpt-5.6-luna").maxOutputTokens(16).build();

		ChatResponse response = this.chatModel.call(new Prompt("Tell me a long story about a dragon.", options));

		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("LENGTH");
		// The native status and reason the portable finish reason was derived from.
		assertThat(response.getMetadata().<String>get(OpenAiResponsesMetadata.STATUS)).isEqualTo("incomplete");
		assertThat(response.getResult().getMetadata().<String>get(OpenAiResponsesMetadata.INCOMPLETE_REASON))
			.isEqualTo("max_output_tokens");
	}

	@Test
	void imageInput() {
		var media = Media.builder().mimeType(MimeTypeUtils.IMAGE_PNG).data(new ClassPathResource("/test.png")).build();
		var message = UserMessage.builder().text("Describe this image in one word.").media(List.of(media)).build();

		ChatResponse response = this.chatModel.call(new Prompt(List.of(message)));

		assertThat(response.getResult().getOutput().getText()).isNotBlank();
	}

	/**
	 * The other input type the endpoint accepts, and the one the mapper routes down its
	 * {@code input_file} branch rather than {@code input_image}.
	 */
	@Test
	void pdfInput() {
		var media = Media.builder()
			.mimeType(MimeTypeUtils.parseMimeType("application/pdf"))
			.data(new ClassPathResource("/spring-ai-reference-overview.pdf"))
			.name("spring-ai-reference-overview.pdf")
			.build();
		var message = UserMessage.builder()
			.text("Summarize the given document in one sentence.")
			.media(List.of(media))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(List.of(message)));

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("Spring AI", "portable API");
	}

	/**
	 * Structured output has to keep working with no user-facing API change:
	 * {@code response_format} becomes {@code text.format} underneath.
	 */
	@Test
	void structuredOutputThroughEntity() {
		ActorFilms films = ChatClient.create(this.chatModel)
			.prompt("Generate the filmography of exactly 5 movies for Tom Hanks.")
			.call()
			.entity(ActorFilms.class);

		assertThat(films).isNotNull();
		assertThat(films.actor()).containsIgnoringCase("Hanks");
		assertThat(films.movies()).hasSize(5);
	}

	/**
	 * The scenario that motivates the whole model: reasoning together with tool calling,
	 * which Chat Completions no longer serves on recent models. Multi-round state flows
	 * through {@code ToolCallingAdvisor}, and the encrypted reasoning item is replayed
	 * with the tool result.
	 */
	@Test
	void reasoningWithToolCallingThroughTheAdvisor() {
		var options = OpenAiResponsesChatOptions.builder()
			.model("gpt-5.6-luna")
			.reasoningEffort("low")
			.reasoningSummary("auto")
			.build();

		String answer = ChatClient.create(this.chatModel)
			.prompt()
			.options(options.mutate())
			.advisors(ToolCallingAdvisor.builder().build())
			.tools(new WeatherTools())
			.user("What is the weather in Paris? Answer with the temperature (in Celsius) only.")
			.call()
			.content();

		assertThat(answer).contains("18");
	}

	@Test
	void hostedWebSearchDoesNotLookLikeALocalToolCall() {
		var options = OpenAiResponsesChatOptions.builder()
			.model("gpt-5.6-luna")
			.hostedTools(HostedTool.WebSearch.of())
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt("What is the latest Spring AI release? Cite a source.", options));

		AssistantMessage message = response.getResult().getOutput();
		ChatGenerationMetadata metadata = response.getResult().getMetadata();
		assertThat(message.hasToolCalls()).isFalse();
		assertThat(message.getText()).isNotBlank();

		// The hosted call surfaces as metadata only, and the citations it produced are the
		// reason to enable the tool in the first place.
		assertThat(metadata.<List<Map<String, Object>>>get(OpenAiResponsesMetadata.HOSTED_TOOL_CALLS))
			.as("transcript %s", message.getParts())
			.isNotEmpty()
			.allSatisfy(hostedCall -> assertThat(hostedCall).containsKeys("type", "id", "status"));
		assertThat(metadata.<List<Map<String, Object>>>get(OpenAiResponsesMetadata.ANNOTATIONS))
			.as("answer '%s'", message.getText())
			.isNotEmpty();
	}

	/**
	 * The streaming counterpart of {@link #reasoningWithToolCallingThroughTheAdvisor()},
	 * and the path with the most moving parts: the assembler stamps each item with its
	 * output index, the aggregator merges the deltas of a round into one message, and the
	 * encrypted reasoning item of round one has to be replayed with the tool result in
	 * round two.
	 */
	@Test
	void streamsReasoningWithToolCallingThroughTheAdvisor() {
		var options = OpenAiResponsesChatOptions.builder()
			.model("gpt-5.6-luna")
			.reasoningEffort("low")
			.reasoningSummary("auto")
			.build();

		String answer = ChatClient.create(this.chatModel)
			.prompt()
			.options(options.mutate())
			.advisors(ToolCallingAdvisor.builder().build())
			.tools(new WeatherTools())
			.user("What is the weather in Paris? Answer with the temperature (in Celsius) only.")
			.stream()
			.content()
			.collectList()
			.block()
			.stream()
			.reduce("", String::concat);

		assertThat(answer).contains("18");
	}

	/**
	 * What the encrypted payload exists for. OpenAI rejects a reasoning item it cannot
	 * match against the one it issued, so this turn only completes if the encrypted
	 * content and the item id both survived the round trip through the assistant message.
	 * The request succeeding is the assertion; the answer text is the model's business.
	 */
	@Test
	void reasoningIsReplayedOnTheNextUserTurn() {
		var options = OpenAiResponsesChatOptions.builder()
			.model("gpt-5.6-luna")
			.reasoningEffort("high")
			.reasoningSummary("auto")
			.build();

		UserMessage first = new UserMessage(REASONING_QUESTION);
		AssistantMessage reasoned = this.chatModel.call(new Prompt(List.of(first), options)).getResult().getOutput();

		assertThat(reasoned.getReasoning()).as("transcript %s", reasoned.getParts()).isNotEmpty();
		// Reasoning precedes the answer it justified, which is the order it gets replayed in.
		assertThat(reasoned.getParts().get(0)).isInstanceOf(ReasoningPart.class);

		ChatResponse followUp = this.chatModel.call(new Prompt(
				List.of(first, reasoned, new UserMessage("Add 30 minutes to that. Answer with the time only.")),
				options));

		assertThat(followUp.getResult().getOutput().getText()).isNotBlank();
		assertThat(followUp.getMetadata().getId()).startsWith("resp_");
	}

	/**
	 * The same replay, but with the first turn streamed: the reasoning part the aggregator
	 * assembles from deltas has to be as replayable as the one a single call returns.
	 */
	@Test
	void streamedReasoningIsReplayedOnTheNextUserTurn() {
		var options = OpenAiResponsesChatOptions.builder()
			.model("gpt-5.6-luna")
			.reasoningEffort("high")
			.reasoningSummary("auto")
			.build();

		UserMessage first = new UserMessage(REASONING_QUESTION);
		AtomicReference<ChatResponse> aggregated = new AtomicReference<>();
		new MessageAggregator().aggregate(this.chatModel.stream(new Prompt(List.of(first), options)), aggregated::set)
			.blockLast();

		AssistantMessage streamed = aggregated.get().getResult().getOutput();
		assertThat(streamed.getReasoning()).as("transcript %s", streamed.getParts())
			.isNotEmpty()
			.allSatisfy(reasoning -> assertThat(reasoning.replayableTo(OpenAiResponsesMetadata.PROVIDER)).isTrue());

		ChatResponse followUp = this.chatModel.call(new Prompt(
				List.of(first, streamed, new UserMessage("Add 30 minutes to that. Answer with the time only.")),
				options));

		assertThat(followUp.getResult().getOutput().getText()).isNotBlank();
	}

	/**
	 * A history assembled by another provider, which is what a shared chat memory hands
	 * over after a model switch. An Anthropic signature replayed as an OpenAI reasoning
	 * item is a hard API error, so the part has to be dropped rather than forwarded.
	 */
	@Test
	void reasoningFromAnotherProviderIsSkippedRatherThanRejected() {
		AssistantMessage foreign = AssistantMessage.builder()
			.part(new ReasoningPart("The user is asking about capitals.", null,
					new OpaquePayload("anthropic", "signature", "EqQBCkYIBBgCIkA"), Map.of()))
			.content("Paris.")
			.build();

		ChatResponse response = this.chatModel
			.call(new Prompt(List.of(new UserMessage("What is the capital of France?"), foreign,
					new UserMessage("And of Italy? Answer with a single word."))));

		assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("Rome");
	}

	/**
	 * Tool calling without the advisor, which is how an application that owns its own
	 * execution loop drives the model. It pins the two things such a loop depends on: the
	 * tool call carries the {@code call_id} the API expects back, not the item id, and an
	 * assistant turn holding reasoning plus a tool call replays alongside the result.
	 */
	@Test
	void aToolCallCanBeAnsweredByHandAndReplayedWithItsReasoning() {
		var options = OpenAiResponsesChatOptions.builder()
			.model("gpt-5.6-luna")
			.reasoningEffort("low")
			.reasoningSummary("auto")
			.toolCallbacks(ToolCallbacks.from(new WeatherTools()))
			.build();

		UserMessage question = new UserMessage(
				"What is the weather in Paris? Answer with the temperature (in Celsius) only.");
		AssistantMessage withCall = this.chatModel.call(new Prompt(List.of(question), options))
			.getResult()
			.getOutput();

		assertThat(withCall.hasToolCalls()).as("transcript %s", withCall.getParts()).isTrue();
		AssistantMessage.ToolCall call = withCall.getToolCalls().get(0);
		assertThat(call.name()).isEqualTo("getCurrentWeather");
		assertThat(call.id()).startsWith("call_");

		ToolResponseMessage result = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse(call.id(), call.name(), "18")))
			.build();

		ChatResponse answered = this.chatModel.call(new Prompt(List.of(question, withCall, result), options));

		assertThat(answered.getResult().getOutput().getText()).contains("18");
	}

	/**
	 * Two calls in one turn, which the mapper has to turn into one output item per tool
	 * result rather than a single merged one.
	 */
	@Test
	void parallelToolCallsAreAllAnswered() {
		String answer = ChatClient.create(this.chatModel)
			.prompt()
			.advisors(ToolCallingAdvisor.builder().build())
			.tools(new WeatherTools())
			.user("What is the weather in Paris and in Tokyo? Give both temperatures in Celsius.")
			.call()
			.content();

		assertThat(answer).contains("18").contains("25");
	}

	/**
	 * A hosted tool and a local one in the same request. The hosted call runs server-side
	 * and must not come back as something {@link ToolCallingAdvisor} tries to execute,
	 * while the local tool still round-trips through the advisor.
	 */
	@Test
	void hostedAndLocalToolsCoexistInOneRequest() {
		var options = OpenAiResponsesChatOptions.builder()
			.model("gpt-5.6-luna")
			.hostedTools(HostedTool.WebSearch.of())
			.build();

		String answer = ChatClient.create(this.chatModel)
			.prompt()
			.options(options.mutate())
			.advisors(ToolCallingAdvisor.builder().build())
			.tools(new WeatherTools())
			.user("Use your weather tool for the temperature in Paris, and search the web for the "
					+ "latest Spring AI version. Give the temperature in Celsius and the version.")
			.call()
			.content();

		assertThat(answer).contains("18");
	}

	/**
	 * The other hosted tool that needs no external setup. The prompt has to insist on the
	 * tool, because the model can reach the answer without running any code.
	 */
	@Test
	void hostedCodeInterpreterRunsServerSide() {
		var options = OpenAiResponsesChatOptions.builder()
			.model("gpt-5.6-luna")
			.hostedTools(HostedTool.CodeInterpreter.of())
			.build();

		ChatResponse response = this.chatModel.call(new Prompt("Use the code interpreter to compute the 30th "
				+ "Fibonacci number, where fib(1) = fib(2) = 1. Answer with the number only.", options));

		assertThat(response.getResult().getOutput().getText()).contains("832040");
		assertThat(response.getResult()
			.getMetadata()
			.<List<Map<String, Object>>>get(OpenAiResponsesMetadata.HOSTED_TOOL_CALLS))
			.as("transcript %s", response.getResult().getOutput().getParts())
			.anySatisfy(hostedCall -> assertThat(hostedCall).containsEntry("type", "code_interpreter_call"));
	}

	/**
	 * The mapper's other image branch: a URL is forwarded as an image url for OpenAI to
	 * fetch, rather than inlined as a data uri the way {@link #imageInput()} is.
	 */
	@Test
	void imageInputFromARemoteUrl() {
		var media = Media.builder()
			.mimeType(MimeTypeUtils.IMAGE_PNG)
			.data(URI.create("https://docs.spring.io/spring-ai/reference/_images/multimodal.test.png"))
			.build();
		var message = UserMessage.builder()
			.text("Explain what do you see on this picture?")
			.media(List.of(media))
			.build();

		ChatResponse response = this.chatModel.call(new Prompt(List.of(message)));

		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");
	}

	record ActorFilms(@Nullable String actor, @Nullable List<String> movies) {
	}

	static class WeatherTools {

		@Tool(description = "Get the current weather in a city, in degrees Celsius")
		String getCurrentWeather(@ToolParam(description = "The city name") String city) {
			return "Tokyo".equalsIgnoreCase(city) ? "25" : "18";
		}

	}

}

package org.springframework.ai.watsonx;

import static org.mockito.Mockito.*;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.watsonx.api.*;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author Pablo Sanchidrian Herrera
 * @author John Jairo Moreno Rojas
 */
public class WatsonxChatClientTest {


	@Test
	public void testCreateRequestWithNoModelId() {
		var options = ChatOptionsBuilder.builder().withTemperature(0.9f).withTopK(100).withTopP(0.6f).build();

		Prompt prompt = new Prompt("Test message", options);

		Exception exception = Assert.assertThrows(IllegalArgumentException.class, () -> {
			WatsonxAIRequest request = WatsonxChatClient.request(prompt);
		});
	}

	@Test
	public void testCreateRequestSuccessfullyWithDefaultParams() {

		String msg = "Test message";

		WatsonxAIOptions modelOptions = WatsonxAIOptions.create().withModel("meta-llama/llama-2-70b-chat");
		Prompt prompt = new Prompt(msg, modelOptions);

		WatsonxAIRequest request = WatsonxChatClient.request(prompt);

		Assert.assertEquals(request.getModelId(), "meta-llama/llama-2-70b-chat");
		assertThat(request.getParameters().get("decoding_method")).isEqualTo("greedy");
		assertThat(request.getParameters().get("temperature")).isEqualTo(0.7);
		assertThat(request.getParameters().get("top_p")).isEqualTo(1.0);
		assertThat(request.getParameters().get("top_k")).isEqualTo(50);
		assertThat(request.getParameters().get("max_new_tokens")).isEqualTo(20);
		assertThat(request.getParameters().get("min_new_tokens")).isEqualTo(0);
		assertThat(request.getParameters().get("stop_sequences")).isInstanceOf(List.class);
		Assert.assertEquals(request.getParameters().get("stop_sequences"), List.of());
		assertThat(request.getParameters().get("random_seed")).isNull();
	}

	@Test
	public void testCreateRequestSuccessfullyWithNonDefaultParams() {

		String msg = "Test message";

		WatsonxAIOptions modelOptions = WatsonxAIOptions.create()
			.withModel("meta-llama/llama-2-70b-chat")
			.withDecodingMethod("sample")
			.withTemperature(0.1f)
			.withTopP(0.2f)
			.withTopK(10)
			.withMaxNewTokens(30)
			.withMinNewTokens(10)
			.withRepetitionPenalty(1.4f)
			.withStopSequences(List.of("\n\n\n"))
			.withRandomSeed(4);

		Prompt prompt = new Prompt(msg, modelOptions);

		WatsonxAIRequest request = WatsonxChatClient.request(prompt);

		Assert.assertEquals(request.getModelId(), "meta-llama/llama-2-70b-chat");
		assertThat(request.getParameters().get("decoding_method")).isEqualTo("sample");
		assertThat(request.getParameters().get("temperature")).isEqualTo(0.1);
		assertThat(request.getParameters().get("top_p")).isEqualTo(0.2);
		assertThat(request.getParameters().get("top_k")).isEqualTo(10);
		assertThat(request.getParameters().get("max_new_tokens")).isEqualTo(30);
		assertThat(request.getParameters().get("min_new_tokens")).isEqualTo(10);
		assertThat(request.getParameters().get("stop_sequences")).isInstanceOf(List.class);
		Assert.assertEquals(request.getParameters().get("stop_sequences"), List.of("\n\n\n"));
		assertThat(request.getParameters().get("random_seed")).isEqualTo(4);
	}

	@Test
	public void testCreateRequestSuccessfullyWithChatDisabled() {

		String msg = "Test message";

		WatsonxAIOptions modelOptions = WatsonxAIOptions.create()
			.withModel("meta-llama/llama-2-70b-chat")
			.withDecodingMethod("sample")
			.withTemperature(0.1f)
			.withTopP(0.2f)
			.withTopK(10)
			.withMaxNewTokens(30)
			.withMinNewTokens(10)
			.withRepetitionPenalty(1.4f)
			.withStopSequences(List.of("\n\n\n"))
			.withRandomSeed(4);

		Prompt prompt = new Prompt(msg, modelOptions);

		WatsonxAIRequest request = WatsonxChatClient.request(prompt);

		Assert.assertEquals(request.getModelId(), "meta-llama/llama-2-70b-chat");
		assertThat(request.getInput()).isEqualTo(msg);
		assertThat(request.getParameters().get("decoding_method")).isEqualTo("sample");
		assertThat(request.getParameters().get("temperature")).isEqualTo(0.1);
		assertThat(request.getParameters().get("top_p")).isEqualTo(0.2);
		assertThat(request.getParameters().get("top_k")).isEqualTo(10);
		assertThat(request.getParameters().get("max_new_tokens")).isEqualTo(30);
		assertThat(request.getParameters().get("min_new_tokens")).isEqualTo(10);
		assertThat(request.getParameters().get("stop_sequences")).isInstanceOf(List.class);
		Assert.assertEquals(request.getParameters().get("stop_sequences"), List.of("\n\n\n"));
		assertThat(request.getParameters().get("random_seed")).isEqualTo(4);
	}

	@Test
	public void testCallMethod() {
		WatsonxAIApi mockChatApi = mock(WatsonxAIApi.class);
		WatsonxChatClient client = new WatsonxChatClient(mockChatApi);

		Prompt prompt = new Prompt(List.of(new SystemMessage("Your prompt here")),
				WatsonxAIOptions.create().withModel("google/flan-ul2"));

		WatsonxAIOptions parameters = WatsonxAIOptions.create().withModel("google/flan-ul2");

		WatsonxAIResults fakeResults = new WatsonxAIResults("LLM response", 4, 3, "max_tokens");

		WatsonxAIResponse fakeResponse = new WatsonxAIResponse("google/flan-ul2", new Date(), List.of(fakeResults),
				Map.of("warnings", List.of(Map.of("message", "the message", "id", "disclaimer_warning"))));

		when(mockChatApi.generate(any(WatsonxAIRequest.class))).thenReturn(fakeResponse);

		Generation expectedGenerator = new Generation("LLM response")
			.withGenerationMetadata(ChatGenerationMetadata.from("max_tokens",
					Map.of("warnings", List.of(Map.of("message", "the message", "id", "disclaimer_warning")))));

		ChatResponse expectedResponse = new ChatResponse(List.of(expectedGenerator));
		ChatResponse response = client.call(prompt);

		Assert.assertEquals(expectedResponse.getResults().size(), response.getResults().size());
		Assert.assertEquals(expectedResponse.getResult().getOutput(), response.getResult().getOutput());
	}

	@Test
	public void testStreamMethod() {
		WatsonxAIApi mockChatApi = mock(WatsonxAIApi.class);
		WatsonxChatClient client = new WatsonxChatClient(mockChatApi);

		Prompt prompt = new Prompt(List.of(new SystemMessage("Your prompt here")),
				WatsonxAIOptions.create().withModel("google/flan-ul2"));

		WatsonxAIOptions parameters = WatsonxAIOptions.create().withModel("google/flan-ul2");

		WatsonxAIResults fakeResultsFirst = new WatsonxAIResults("LLM resp", 0, 0, "max_tokens");
		WatsonxAIResults fakeResultsSecond = new WatsonxAIResults("onse", 4, 3, "not_finished");

		WatsonxAIResponse fakeResponseFirst = new WatsonxAIResponse("google/flan-ul2", new Date(),
				List.of(fakeResultsFirst),
				Map.of("warnings", List.of(Map.of("message", "the message", "id", "disclaimer_warning"))));
		WatsonxAIResponse fakeResponseSecond = new WatsonxAIResponse("google/flan-ul2", new Date(),
				List.of(fakeResultsSecond), null);

		Flux<WatsonxAIResponse> fakeResponse = Flux.just(fakeResponseFirst, fakeResponseSecond);
		when(mockChatApi.generateStreaming(any(WatsonxAIRequest.class))).thenReturn(fakeResponse);

		Generation firstGen = new Generation("LLM resp")
			.withGenerationMetadata(ChatGenerationMetadata.from("max_tokens",
					Map.of("warnings", List.of(Map.of("message", "the message", "id", "disclaimer_warning")))));
		Generation secondGen = new Generation("onse");

		Flux<ChatResponse> response = client.stream(prompt);

		StepVerifier.create(response).assertNext(current -> {

			ChatResponse expected = new ChatResponse(List.of(firstGen));

			Assert.assertEquals(expected.getResults().size(), current.getResults().size());
			Assert.assertEquals(expected.getResult().getOutput(), current.getResult().getOutput());
		}).assertNext(current -> {
			ChatResponse expected = new ChatResponse(List.of(secondGen));

			Assert.assertEquals(expected.getResults().size(), current.getResults().size());
			Assert.assertEquals(expected.getResult().getOutput(), current.getResult().getOutput());
		}).expectComplete().verify();

	}

}

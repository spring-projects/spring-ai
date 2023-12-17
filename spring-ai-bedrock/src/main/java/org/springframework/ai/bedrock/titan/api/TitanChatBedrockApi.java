/*
 * Copyright 2023-2023 the original author or authors.
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
// @formatter:off
package org.springframework.ai.bedrock.titan.api;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.api.AbstractBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatResponse.CompletionReason;

/**
 * Java client for the Bedrock Titan chat model.
 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-text.html
 * <p>
 * https://docs.aws.amazon.com/bedrock/latest/userguide/titan-text-models.html
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class TitanChatBedrockApi
		extends
		AbstractBedrockApi<TitanChatBedrockApi.TitanChatRequest, TitanChatBedrockApi.TitanChatResponse, TitanChatBedrockApi.TitanChatResponseChunk> {

	TitanChatBedrockApi(String modelId, String region) {
		super(modelId, region);
	}

	/**
	 * TitanChatRequest encapsulates the request parameters for the Titan chat model.
	 *
	 * @param inputText The prompt to use for the chat.
	 * @param textGenerationConfig The text generation configuration.
	 */
	@JsonInclude(Include.NON_NULL)
	public record TitanChatRequest(
			@JsonProperty("inputText") String inputText,
			@JsonProperty("textGenerationConfig") TextGenerationConfig textGenerationConfig) {

		/**
		 * Titan request text generation configuration.
		 * https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-text.html
		 *
		 * @param temperature The temperature value controls the randomness of the generated text.
		 * @param topP The topP value controls the diversity of the generated text. Use a lower value to ignore less
		 * probable options.
		 * @param maxTokenCount The maximum number of tokens to generate.
		 * @param stopSequences A list of sequences to stop the generation at. Specify character sequences to indicate
		 * where the model should stop. Use the | (pipe) character to separate different sequences (maximum 20
		 * characters).
		 */
		@JsonInclude(Include.NON_NULL)
		public record TextGenerationConfig(
				@JsonProperty("temperature") Float temperature,
				@JsonProperty("topP") Float topP,
				@JsonProperty("maxTokenCount") Integer maxTokenCount,
				@JsonProperty("stopSequences") List<String> stopSequences) {
		}
	}

	/**
	 * TitanChatResponse encapsulates the response parameters for the Titan chat model.
	 *
	 * @param inputTextTokenCount The number of tokens in the input text.
	 * @param results The list of generated responses.
	 */
	@JsonInclude(Include.NON_NULL)
	public record TitanChatResponse(
			@JsonProperty("inputTextTokenCount") Integer inputTextTokenCount,
			@JsonProperty("results") List<Result> results) {

		/**
		 * Titan response result.
		 *
		 * @param tokenCount The number of tokens in the generated text.
		 * @param outputText The generated text.
		 * @param completionReason The reason the response finished being generated.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Result(
				@JsonProperty("tokenCount") Integer tokenCount,
				@JsonProperty("outputText") String outputText,
				@JsonProperty("completionReason") CompletionReason completionReason) {
		}

		/**
		 * The reason the response finished being generated.
		 */
		public enum CompletionReason {
			/**
			 * The response was fully generated.
			 */
			FINISH,
			/**
			 * The response was truncated because of the response length you set.
			 */
			LENGTH
		}
	}

	/**
	 * Titan chat model streaming response.
	 *
	 * @param outputText The generated text in this chunk.
	 * @param index The index of the chunk in the streaming response.
	 * @param inputTextTokenCount The number of tokens in the prompt.
	 * @param totalOutputTextTokenCount The number of tokens in the response.
	 * @param completionReason The reason the response finished being generated.
	 */
	@JsonInclude(Include.NON_NULL)
	public record TitanChatResponseChunk(
			@JsonProperty("outputText") String outputText,
			@JsonProperty("index") Integer index,
			@JsonProperty("inputTextTokenCount") Integer inputTextTokenCount,
			@JsonProperty("totalOutputTextTokenCount") Integer totalOutputTextTokenCount,
			@JsonProperty("completionReason") CompletionReason completionReason,
			@JsonProperty("amazon-bedrock-invocationMetrics") AmazonBedrockInvocationMetrics amazonBedrockInvocationMetrics) {
	}

	/**
	 * Titan models version.
	 */
	public enum TitanChatCompletionModel {

		/**
		 * amazon.titan-text-lite-v1
		 */
		TITAN_TEXT_LITE_V1("amazon.titan-text-lite-v1"),

		/**
		 * amazon.titan-text-express-v1
		 */
		TITAN_TEXT_EXPRESS_V1("amazon.titan-text-express-v1");

		private final String id;

		/**
		 * @return The model id.
		 */
		public String id() {
			return id;
		}

		TitanChatCompletionModel(String value) {
			this.id = value;
		}
	}

	@Override
	public TitanChatResponse chatCompletion(TitanChatRequest request) {
		return this.internalInvocation(request, TitanChatResponse.class);
	}

	@Override
	public Flux<TitanChatResponseChunk> chatCompletionStream(TitanChatRequest request) {
		return this.internalInvocationStream(request, TitanChatResponseChunk.class);
	}

	/**
	 * TODO: to remove.
	 *
	 * @param args blank.
	 */
	public static void main(String[] args) {
		// TitanChatBedrockApi titanBedrockApi = new
		// TitanChatBedrockApi(TitanChatCompletionModel.TITAN_TEXT_LITE_V1.id(),
		// Region.US_EAST_1.id());
		TitanChatBedrockApi titanBedrockApi = new TitanChatBedrockApi(
				TitanChatCompletionModel.TITAN_TEXT_EXPRESS_V1.id(),
				Region.EU_CENTRAL_1.id());

		TitanChatRequest titanChatRequest = new TitanChatRequest(
				"What is the capital of Bulgaria and what is the size? What it the national anthem",
				new TitanChatRequest.TextGenerationConfig(0.5f, 0.9f, 100, List.of("|")));

		TitanChatResponse titanChatResponse = titanBedrockApi.chatCompletion(titanChatRequest);
		System.out.println(titanChatResponse);

		Flux<TitanChatResponseChunk> flux = titanBedrockApi.chatCompletionStream(titanChatRequest);
		List<TitanChatResponseChunk> list = flux.collectList().block();
		System.out.println(list.stream().map(e -> e.toString()).collect(Collectors.joining("\n")));

	}
}
// @formatter:on
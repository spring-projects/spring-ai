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

package org.springframework.ai.vertexai.palm2.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

// @formatter:off
/**
 * Vertex AI API client for the Generative Language model.
 * https://developers.generativeai.google/api/rest/generativelanguage
 * https://cloud.google.com/vertex-ai/docs/generative-ai/learn/streaming
 *
 * Provides methods to generate a response from the model given an input
 * https://developers.generativeai.google/api/rest/generativelanguage/models/generateMessage
 *
 * as well as to generate embeddings for the input text:
 * https://developers.generativeai.google/api/rest/generativelanguage/models/embedText
 *
 *
 * Supported models:
 *
 * <pre>
 * 		name=models/chat-bison-001,
 * 		version=001,
 * 		displayName=Chat Bison,
 * 		description=Chat-optimized generative language model.,
 * 		inputTokenLimit=4096,
 * 		outputTokenLimit=1024,
 * 		supportedGenerationMethods=[generateMessage, countMessageTokens],
 * 		temperature=0.25,
 * 		topP=0.95,
 *		topK=40
 *
 * 		name=models/text-bison-001,
 *		version=001,
 *		displayName=Text Bison,
 *		description=Model targeted for text generation.,
 * 		inputTokenLimit=8196,
 *		outputTokenLimit=1024,
 *		supportedGenerationMethods=[generateText, countTextTokens, createTunedTextModel],
 *		temperature=0.7,
 *		topP=0.95,
 *		topK=40
 *
 * 		name=models/embedding-gecko-001,
 * 		version=001,
 * 		displayName=Embedding Gecko, description=Obtain a distributed representation of a text.,
 * 		inputTokenLimit=1024,
 * 		outputTokenLimit=1,
 * 		supportedGenerationMethods=[embedText, countTextTokens],
 * 		temperature=null,
 * 		topP=null,
 * 		topK=null
 * </pre>
 *
 * @author Christian Tzolov
 */
public class VertexAiApi {

	/**
	 * The default generation model. This model is used to generate responses for the
	 * input text.
	 */
	public static final String DEFAULT_GENERATE_MODEL = "chat-bison-001";

	/**
	 * The default embedding model. This model is used to generate embeddings for the
	 * input text.
	 */
	public static final String DEFAULT_EMBEDDING_MODEL = "embedding-gecko-001";

	/**
	 * The default base URL for accessing the Vertex AI API.
	 */
	public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta3";

	private final RestClient restClient;

	private final String apiKey;

	private final String chatModel;

	private final String embeddingModel;

	/**
	 * Create a new chat completion api.
	 * @param apiKey vertex apiKey.
	 */
	public VertexAiApi(String apiKey) {
		this(DEFAULT_BASE_URL, apiKey, DEFAULT_GENERATE_MODEL, DEFAULT_EMBEDDING_MODEL, RestClient.builder());
	}

	/**
	 * Create a new chat completion api.
	 * @param baseUrl api base URL.
	 * @param apiKey vertex apiKey.
	 * @param model vertex model.
	 * @param embeddingModel vertex embedding model.
	 * @param restClientBuilder RestClient builder.
	 */
	public VertexAiApi(String baseUrl, String apiKey, String model, String embeddingModel,
			RestClient.Builder restClientBuilder) {

		this.chatModel = model;
		this.embeddingModel = embeddingModel;
		this.apiKey = apiKey;

		Consumer<HttpHeaders> jsonContentHeaders = headers -> {
			headers.setAccept(List.of(MediaType.APPLICATION_JSON));
			headers.setContentType(MediaType.APPLICATION_JSON);
		};

		ResponseErrorHandler responseErrorHandler = new ResponseErrorHandler() {
			@Override
			public boolean hasError(ClientHttpResponse response) throws IOException {
				return response.getStatusCode().isError();
			}

			@Override
			public void handleError(ClientHttpResponse response) throws IOException {
				if (response.getStatusCode().isError()) {
					throw new RuntimeException(String.format("%s - %s", response.getStatusCode().value(),
							new ObjectMapper().readValue(response.getBody(), ResponseError.class)));
				}
			}
		};

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(jsonContentHeaders)
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	/**
	 * Generates a response from the model given an input.
	 * @param request Request body.
	 * @return Response body.
	 */
	@SuppressWarnings("null")
	public GenerateMessageResponse generateMessage(GenerateMessageRequest request) {
		Assert.notNull(request, "The request body can not be null.");

		return this.restClient.post()
			.uri("/models/{model}:generateMessage?key={apiKey}", this.chatModel, this.apiKey)
			.body(request)
			.retrieve()
			.body(GenerateMessageResponse.class);
	}

	/**
	 * Generates a response from the model given an input.
	 * @param text Text to embed.
	 * @return Embedding response.
	 */
	public Embedding embedText(String text) {
		Assert.hasText(text, "The text can not be null or empty.");

		@JsonInclude(Include.NON_NULL)
		record EmbeddingResponse(Embedding embedding) {
		}

		EmbeddingResponse response = this.restClient.post()
				.uri("/models/{model}:embedText?key={apiKey}", this.embeddingModel, this.apiKey)
				.body(Map.of("text", text))
				.retrieve()
				.body(EmbeddingResponse.class);

		return response != null ? response.embedding() : null;
	}

	@JsonInclude(Include.NON_NULL)
	record BatchEmbeddingResponse(List<Embedding> embeddings) {
	}

	/**
	 * Generates a response from the model given an input.
	 * @param texts List of texts to embed.
	 * @return Embedding response containing a list of embeddings.
	 */
	public List<Embedding> batchEmbedText(List<String> texts) {
		Assert.notNull(texts, "The texts can not be null.");


		BatchEmbeddingResponse response = this.restClient.post()
				.uri("/models/{model}:batchEmbedText?key={apiKey}", this.embeddingModel, this.apiKey)
				// https://developers.generativeai.google/api/rest/generativelanguage/models/batchEmbedText#request-body
				.body(Map.of("texts", texts))
				.retrieve()
				.body(BatchEmbeddingResponse.class);

		return response != null ? response.embeddings() : null;
	}

	/**
	 * Returns the number of tokens in the message prompt.
	 * @param prompt Message prompt to count tokens for.
	 * @return Number of tokens in the message prompt.
	 */
	public Integer countMessageTokens(MessagePrompt prompt) {

		Assert.notNull(prompt, "The message prompt can not be null.");

		record TokenCount(@JsonProperty("tokenCount") Integer tokenCount) {
		}

		TokenCount tokenCountResponse = this.restClient.post()
				.uri("/models/{model}:countMessageTokens?key={apiKey}", this.chatModel, this.apiKey)
				.body(Map.of("prompt", prompt))
				.retrieve()
				.body(TokenCount.class);

		return tokenCountResponse != null ? tokenCountResponse.tokenCount() : null;
	}

	/**
	 * Returns the list of models available for use.
	 * @return List of models available for use.
	 */
	public List<String> listModels() {

		@JsonInclude(Include.NON_NULL)
		record ModelList(@JsonProperty("models") List<ModelName> models) {
			record ModelName(String name) {
			}
		}


		ModelList modelList = this.restClient.get()
			.uri("/models?key={apiKey}", this.apiKey)
			.retrieve()
			.body(ModelList.class);

		return modelList == null ? List.of() :
			modelList.models().stream()
			.map(ModelList.ModelName::name)
			.toList();
	}

	/**
	 * Returns the model details.
	 * @param modelName Name of the model to get details for.
	 * @return Model details.
	 */
	public Model getModel(String modelName) {

		Assert.hasText(modelName, "The model name can not be null or empty.");

		if (modelName.startsWith("models/")) {
			modelName = modelName.substring("models/".length());
		}

		return this.restClient.get()
			.uri("/models/{model}?key={apiKey}", modelName, this.apiKey)
			.retrieve()
			.body(Model.class);
	}

	/**
	 * API error response.
	 *
	 * @param error Error details.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ResponseError(
			@JsonProperty("error") Error error) {

		/**
		 * Error details.
		 *
		 * @param message Error message.
		 * @param code Error code.
		 * @param status Error status.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Error(
				@JsonProperty("message") String message,
				@JsonProperty("code") String code,
				@JsonProperty("status") String status) {
		}
	}

	/**
	 * Information about a Generative Language Model.
	 *
	 * @param name The resource name of the Model. Format: `models/{model} with a {model}
	 * naming convention of:`
	 *
	 * <pre>
	 * {baseModelId}-{version}
	 * </pre>
	 * @param baseModelId The name of the base model, pass this to the generation request.
	 * @param version The version of the model. This represents the major version.
	 * @param displayName The human-readable name of the model. E.g. "Chat Bison". The
	 * name can be up to 128 characters long and can consist of any UTF-8 characters.
	 * @param description A short description of the model.
	 * @param inputTokenLimit Maximum number of input tokens allowed for this model.
	 * @param outputTokenLimit Maximum number of output tokens allowed for this model.
	 * @param supportedGenerationMethods List of supported generation methods for this
	 * model. The method names are defined as Pascal case strings, such as generateMessage
	 * which correspond to API methods.
	 * @param temperature Controls the randomness of the output. Values can range over
	 * [0.0,1.0], inclusive. A value closer to 1.0 will produce responses that are more
	 * varied, while a value closer to 0.0 will typically result in less surprising
	 * responses from the model. This value specifies default to be used by the backend
	 * while making the call to the model.
	 * @param topP For Nucleus sampling. Nucleus sampling considers the smallest set of
	 * tokens whose probability sum is at least topP. This value specifies default to be
	 * used by the backend while making the call to the model.
	 * @param topK For Top-k sampling. Top-k sampling considers the set of topK most
	 * probable tokens. This value specifies default to be used by the backend while
	 * making the call to the model.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Model(
			@JsonProperty("name") String name,
			@JsonProperty("baseModelId") String baseModelId,
			@JsonProperty("version") String version,
			@JsonProperty("displayName") String displayName,
			@JsonProperty("description") String description,
			@JsonProperty("inputTokenLimit") Integer inputTokenLimit,
			@JsonProperty("outputTokenLimit") Integer outputTokenLimit,
			@JsonProperty("supportedGenerationMethods") List<String> supportedGenerationMethods,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("topP") Float topP,
			@JsonProperty("topK") Integer topK) {
	}

	/**
	 * A list of floats representing the embedding.
	 *
	 * @param value The embedding values.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Embedding(
			@JsonProperty("value") List<Double> value) {

	}

	/**
	 * The base unit of structured text. A Message includes an author and the content of
	 * the Message. The author is used to tag messages when they are fed to the model as
	 * text.
	 *
	 * @param author (optional) Author of the message. This serves as a key for tagging
	 * the content of this Message when it is fed to the model as text.The author can be
	 * any alphanumeric string.
	 * @param content The text content of the structured Message.
	 * @param citationMetadata (output only) Citation information for model-generated
	 * content in this Message. If this Message was generated as output from the model,
	 * this field may be populated with attribution information for any text included in
	 * the content. This field is used only on output.
	 */
	@JsonInclude(Include.NON_NULL)
	public record Message(
			@JsonProperty("author") String author,
			@JsonProperty("content") String content,
			@JsonProperty("citationMetadata") CitationMetadata citationMetadata) {

		/**
		 * Short-hand constructor for a message without citation metadata.
		 * @param author (optional) Author of the message.
		 * @param content The text content of the structured Message.
		 */
		public Message(String author, String content) {
			this(author, content, null);
		}

		/**
		 * A collection of source attributions for a piece of content.
		 *
		 * Citations to sources for a specific response.
		 */
		@JsonInclude(Include.NON_NULL)
		public record CitationMetadata(
				@JsonProperty("citationSources") List<CitationSource> citationSources) {
		}

		/**
		 * A citation to a source for a portion of a specific response.
		 *
		 * @param startIndex (optional) Start of segment of the response that is
		 * attributed to this source. Index indicates the start of the segment, measured
		 * in bytes.
		 * @param endIndex (optional) End of the attributed segment, exclusive.
		 * @param uri (optional) URI that is attributed as a source for a portion of the
		 * text.
		 * @param license (optional) License for the GitHub project that is attributed as
		 * a source for segment.License info is required for code citations.
		 */
		@JsonInclude(Include.NON_NULL)
		public record CitationSource(
				@JsonProperty("startIndex") Integer startIndex,
				@JsonProperty("endIndex") Integer endIndex,
				@JsonProperty("uri") String uri,
				@JsonProperty("license") String license) {
		}
	}

	/**
	 * All of the structured input text passed to the model as a prompt.
	 *
	 * A MessagePrompt contains a structured set of fields that provide context for the
	 * conversation, examples of user input/model output message pairs that prime the
	 * model to respond in different ways, and the conversation history or list of
	 * messages representing the alternating turns of the conversation between the user
	 * and the model.
	 *
	 * @param context (optional) Text that should be provided to the model first to ground
	 * the response. If not empty, this context will be given to the model first before
	 * the examples and messages. When using a context be sure to provide it with every
	 * request to maintain continuity. This field can be a description of your prompt to
	 * the model to help provide context and guide the responses. Examples: "Translate the
	 * phrase from English to French." or "Given a statement, classify the sentiment as
	 * happy, sad or neutral." Anything included in this field will take precedence over
	 * message history if the total input size exceeds the model's inputTokenLimit and the
	 * input request is truncated.
	 * @param examples (optional) Examples of what the model should generate. This
	 * includes both user input and the response that the model should emulate. These
	 * examples are treated identically to conversation messages except that they take
	 * precedence over the history in messages: If the total input size exceeds the
	 * model's inputTokenLimit the input will be truncated. Items will be dropped from
	 * messages before examples.
	 * @param messages (optional) A snapshot of the recent conversation history sorted
	 * chronologically. Turns alternate between two authors. If the total input size
	 * exceeds the model's inputTokenLimit the input will be truncated: The oldest items
	 * will be dropped from messages.
	 */
	@JsonInclude(Include.NON_NULL)
	public record MessagePrompt(
			@JsonProperty("context") String context,
			@JsonProperty("examples") List<Example> examples,
			@JsonProperty("messages") List<Message> messages) {

		/**
		 * Shortcut constructor for a message prompt without context.
		 * @param messages The conversation history used by the model.
		 */
		public MessagePrompt(List<Message> messages) {
			this(null, null, messages);
		}

		/**
		 * Shortcut constructor for a message prompt without context.
		 * @param context An input/output example used to instruct the Model. It
		 * demonstrates how the model should respond or format its response.
		 * @param messages The conversation history used by the model.
		 */
		public MessagePrompt(String context, List<Message> messages) {
			this(context, null, messages);
		}

		/**
		 * An input/output example used to instruct the Model. It demonstrates how the
		 * model should respond or format its response.
		 *
		 * @param input An example of an input Message from the user.
		 * @param output An example of an output Message from the model.
		 */
		@JsonInclude(Include.NON_NULL)
		public record Example(
				@JsonProperty("input") Message input,
				@JsonProperty("output") Message output) {
		}
	}

	/**
	 * Message generation request body.
	 *
	 * @param prompt The structured textual input given to the model as a prompt. Given a
	 * prompt, the model will return what it predicts is the next message in the
	 * discussion.
	 * @param temperature (optional) Controls the randomness of the output. Values can
	 * range over [0.0,1.0], inclusive. A value closer to 1.0 will produce responses that
	 * are more varied, while a value closer to 0.0 will typically result in less
	 * surprising responses from the model.
	 * @param candidateCount (optional) The number of generated response messages to
	 * return. This value must be between [1, 8], inclusive. If unset, this will default
	 * to 1.
	 * @param topP (optional) The maximum cumulative probability of tokens to consider
	 * when sampling. The model uses combined Top-k and nucleus sampling. Nucleus sampling
	 * considers the smallest set of tokens whose probability sum is at least topP.
	 * @param topK (optional) The maximum number of tokens to consider when sampling. The
	 * model uses combined Top-k and nucleus sampling. Top-k sampling considers the set of
	 * topK most probable tokens.
	 */
	@JsonInclude(Include.NON_NULL)
	public record GenerateMessageRequest(
			@JsonProperty("prompt") MessagePrompt prompt,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("candidateCount") Integer candidateCount,
			@JsonProperty("topP") Float topP,
			@JsonProperty("topK") Integer topK) {

		/**
		 * Shortcut constructor to create a GenerateMessageRequest with only the prompt
		 * parameter.
		 * @param prompt The structured textual input given to the model as a prompt.
		 */
		public GenerateMessageRequest(MessagePrompt prompt) {
			this(prompt, null, null, null, null);
		}

		/**
		 * Shortcut constructor to create a GenerateMessageRequest with only the prompt
		 * and temperature parameters.
		 * @param prompt The structured textual input given to the model as a prompt.
		 * @param temperature (optional) Controls the randomness of the output.
		 * @param topK (optional) The maximum number of tokens to consider when sampling.
		 */
		public GenerateMessageRequest(MessagePrompt prompt, Float temperature, Integer topK) {
			this(prompt, temperature, null, null, topK);
		}
	}

	/**
	 * The response from the model. This includes candidate messages and conversation
	 * history in the form of chronologically-ordered messages.
	 *
	 * @param candidates Candidate response messages from the model.
	 * @param messages The conversation history used by the model.
	 * @param filters A set of content filtering metadata for the prompt and response
	 * text. This indicates which SafetyCategory(s) blocked a candidate from this
	 * response, the lowest HarmProbability that triggered a block, and the HarmThreshold
	 * setting for that category.
	 */
	@JsonInclude(Include.NON_NULL)
	public record GenerateMessageResponse(
			@JsonProperty("candidates") List<Message> candidates,
			@JsonProperty("messages") List<Message> messages,
			@JsonProperty("filters") List<ContentFilter> filters) {

		/**
		 * Content filtering metadata associated with processing a single request. It
		 * contains a reason and an optional supporting string. The reason may be
		 * unspecified.
		 *
		 * @param reason The reason content was blocked during request processing.
		 * @param message A string that describes the filtering behavior in more detail.
		 */
		@JsonInclude(Include.NON_NULL)
		public record ContentFilter(
				@JsonProperty("reason") BlockedReason reason,
				@JsonProperty("message") String message) {

			/**
			 * Reasons why content may have been blocked.
			 */
			public enum BlockedReason {

				/**
				 * A blocked reason was not specified.
				 */
				BLOCKED_REASON_UNSPECIFIED,
				/**
				 * Content was blocked by safety settings.
				 */
				SAFETY,
				/**
				 * Content was blocked, but the reason is uncategorized.
				 */
				OTHER

			}
		}
	}
}
// @formatter:on
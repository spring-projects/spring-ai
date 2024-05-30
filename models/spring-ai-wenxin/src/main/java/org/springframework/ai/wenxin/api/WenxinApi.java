package org.springframework.ai.wenxin.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午4:51
 * @description:
 */
public class WenxinApi {

	// @formatter:off
	public static final String DEFAULT_CHAT_MODEL = ChatModel.ERNIE_3_5_8K.getValue();

	public static final String DEFAULT_EMBEDDING_MODEL = EmbeddingModel.Embedding_V1.getValue();

	private static final Predicate<String> SSE_DONE_PREDICATE = "[DONE]"::equals;

	private final RestClient restClient;

	private final WebClient webClient;

	private final String accessKey;

	private final String secretKey;

	private WenxinStreamFunctionCallingHelper chunkMerger = new WenxinStreamFunctionCallingHelper();

	public WenxinApi(String accessKey, String secretKey) {
		this(ApiUtils.DEFAULT_BASE_URL, accessKey, secretKey);
	}

	public WenxinApi(String baseUrl, String accessKey, String secretKey) {
		this(baseUrl, RestClient.builder(), WebClient.builder(), accessKey, secretKey);
	}

	public WenxinApi(String baseUrl, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			String accessKey, String secretKey) {
		this(baseUrl, restClientBuilder, webClientBuilder,
				RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER,
				//new CustomResponseErrorHandler(),
				accessKey,
				secretKey);
	}

	public WenxinApi(String baseUrl, RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
			ResponseErrorHandler responseErrorHandler, String accessKey, String secretKey) {

		this.restClient = restClientBuilder
				.baseUrl(baseUrl)
				.defaultHeaders(ApiUtils.getJsonContentHeaders())
				.defaultStatusHandler(responseErrorHandler)
				.build();

		this.webClient = webClientBuilder
				.baseUrl(baseUrl)
				.defaultHeaders(ApiUtils.getJsonContentHeaders())
				.build();

		this.accessKey = accessKey;
		this.secretKey = secretKey;
	}

	public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest chatRequest) {
		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(!chatRequest.stream(), "Request must set the steam property to false.");

		var timestamp = Instant.now();
		var authorization = ApiUtils.generationAuthorization(accessKey, secretKey, timestamp, chatRequest.model(),
				ApiUtils.DEFAULT_BASE_CHAT_URI);

		return this.restClient.post()
				.uri(ApiUtils.DEFAULT_BASE_CHAT_URI + chatRequest.model())
				.headers(headers -> {
					headers.set("x-bce-date", ApiUtils.formatDate(timestamp));
					headers.set("Authorization", authorization);
				})
				.body(chatRequest)
				.retrieve()
				.toEntity(ChatCompletion.class);
	}

	public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest chatRequest) {
		Assert.notNull(chatRequest, "The request body can not be null.");
		Assert.isTrue(chatRequest.stream(), "Request must set the  steam property to true.");

		AtomicBoolean isInsideTool = new AtomicBoolean(false);
		Instant timestamp = Instant.now();
		String authorization = ApiUtils.generationAuthorization(accessKey, secretKey, timestamp, chatRequest.model(),
				ApiUtils.DEFAULT_BASE_CHAT_URI);

		return this.webClient.post()
				.uri(ApiUtils.DEFAULT_BASE_CHAT_URI + chatRequest.model())
				.headers(headers -> {
					headers.set("x-bce-date", ApiUtils.formatDate(timestamp));
					headers.set("Authorization", authorization);
				})
				.body(Mono.just(chatRequest), ChatCompletionRequest.class)
				.retrieve()
				.bodyToFlux(String.class)
				.takeUntil(SSE_DONE_PREDICATE)
				.filter(SSE_DONE_PREDICATE.negate())
				.map(content -> ModelOptionsUtils.jsonToObject(content, ChatCompletionChunk.class))
				.map(chunk -> {
					if (this.chunkMerger.isStreamingToolFunctionCall(chunk)) {
						isInsideTool.set(true);
					}
					return chunk;
				})
				.windowUntil(chunk -> {
					if (isInsideTool.get() && this.chunkMerger.isStreamingToolFunctionCallFinish(chunk)) {
						isInsideTool.set(false);
						return true;
					}
					return !isInsideTool.get();
				})
				.concatMapIterable(window -> {
					Mono<ChatCompletionChunk> monoChunk = window.reduce(
							new ChatCompletionChunk(null, null, null, null, null, null, null, null, null, null, null,
									null, null, null),
							(previous, current) -> this.chunkMerger.merge(previous, current));
					return List.of(monoChunk);
				})
				.flatMap(mono -> mono);
	}

	public enum ChatModel {

		ERNIE_4_8K("completions_pro"),
		ERNIE_4_8K_PREEMPTIVE("completions_pro_preemptive"),
		ERNIE_4_8K_PREVIEW("ernie-4.0-8k-preview"),
		ERNIE_4_8K_0329("ernie-4.0-8k-0329"),
		ERNIE_4_8K_0104("ernie-4.0-8k-0104"),
		ERNIE_3_5_8K("completions"),
		ERNIE_3_5_8K_0205("ernie-3.5-8k-0205"),
		ERNIE_3_5_8K_1222("ernie-3.5-8k-1222"),
		ERNIE_3_5_4K_0205("ernie-3.5-4k-0205"),
		ERNIE_3_5_8K_PREEMPTIVE("completions_preemptive"),
		ERNIE_3_5_8K_Preview("ernie-3.5-8k-preview"),
		ERNIE_3_5_8K_0329("ernie-3.5-8k-0329");

		public final String value;

		ChatModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}

	}

	public enum Role {

		@JsonProperty("user") USER,
		@JsonProperty("assistant") ASSISTANT,
		@JsonProperty("function") FUNCTION

	}

	public enum ChatCompletionFinishReason {

		@JsonProperty("normal") NORMAL,
		@JsonProperty("stop") STOP,
		@JsonProperty("length") LENGTH,
		@JsonProperty("content_filter") CONTENT_FILTER,
		@JsonProperty("function_call") FUNCTION_CALL

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FunctionTool(
			@JsonProperty("name") String name,
			@JsonProperty("description") String description,
			@JsonProperty("parameters") Map<String, Object> parameters,
			@JsonProperty("responses") Map<String, Object> responses,
			@JsonProperty("examples") List<List<Example>> examples) {

		@ConstructorBinding
		public FunctionTool(String name, String description, String jsonSchemaForParameters,
				List<List<Example>> examples) {
			this(name, description, ModelOptionsUtils.jsonToMap(jsonSchemaForParameters), null, examples);
		}

		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record Example(
				@JsonProperty("role") Role role,
				@JsonProperty("content") String content,
				@JsonProperty("name") String name,
				@JsonProperty("function_call") FunctionCall functionCall) {
		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FunctionCall(
			@JsonProperty("name") String name,
			@JsonProperty("arguments") String arguments,
			@JsonProperty("thoughts") String thoughts) {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionRequest(
			@JsonProperty("messages") List<ChatCompletionMessage> messages,
			@JsonProperty("model") String model,
			@JsonProperty("penalty_score") Float penaltyScore,
			@JsonProperty("max_output_tokens") Integer maxOutputTokens,
			@JsonProperty("response_format") ResponseFormat responseFormat,
			@JsonProperty("stop") List<String> stop,
			@JsonProperty("stream") Boolean stream,
			@JsonProperty("temperature") Float temperature,
			@JsonProperty("top_p") Float topP,
			@JsonProperty("functions") List<FunctionTool> functions,
			@JsonProperty("tool_choice") String toolChoice,
			@JsonProperty("user_id") String userId,
			@JsonProperty("system") String system,
			@JsonProperty("disable_search") Boolean disableSearch,
			@JsonProperty("enable_citation") Boolean enableCitation,
			@JsonProperty("enable_trace") Boolean enableTrace) {

		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature) {
			this(messages, model, null, null, null, null, false, temperature, null, null, null, null, null, false,
					false, false);
		}

		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, Float temperature,
				boolean stream) {
			this(messages, model, null, null, null, null, stream, temperature, null, null, null, null, null, false,
					false, false);
		}

		public ChatCompletionRequest(List<ChatCompletionMessage> messages, String model, List<FunctionTool> tools,
				String toolChoice, Boolean disableSearch) {
			this(messages, model, null, null, null, null, false, 0.8f, null, tools, toolChoice, null, null,
					disableSearch, false, false);
		}

		public ChatCompletionRequest(List<ChatCompletionMessage> messages, Boolean stream) {
			this(messages, DEFAULT_CHAT_MODEL, null, null, null, null, stream, null, null, null, null, null, null,
					false, false, false);
		}

		public enum ResponseFormat {

			@JsonProperty("text") TEXT,
			@JsonProperty("json_object") JSON_OBJECT

		}

		public static class ToolChoiceBuilder {

			public static final String DEFAULT_TOOL_CHOICE = "auto";

			public static final String NONE = "none";

			public static String FUNCTION(String functionName) {
				return ModelOptionsUtils.toJsonString(
						Map.of(
								"type", "function",
								"function",
								Map.of("name", functionName)
						)
				);
			}

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionMessage(
			@JsonProperty("content") String content,
			@JsonProperty("role") Role role,
			@JsonProperty("name") String name,
			@JsonProperty("function_call") FunctionCall functionCall) {

		public ChatCompletionMessage(String content, Role role) {
			this(content, role, null, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletion(
			@JsonProperty("id") String id,
			@JsonProperty("object") String object,
			@JsonProperty("created") Long created,
			@JsonProperty("sentence_id") String sentenceId,
			@JsonProperty("is_end") Boolean isEnd,
			@JsonProperty("is_truncated") Boolean isTruncated,
			@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
			@JsonProperty("search_info") SearchInfo searchInfo,
			@JsonProperty("result") String result,
			@JsonProperty("need_clear_history") Boolean needClearHistory,
			@JsonProperty("flag") Integer flag,
			@JsonProperty("ban_round") Integer banRound,
			@JsonProperty("usage") Usage usage,
			@JsonProperty("function_call") FunctionCall functionCall) {

		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record SearchInfo(@JsonProperty("search_results") List<SearchResult> searchResults) {

		}

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record SearchResult(
			@JsonProperty("index") Integer index,
			@JsonProperty("url") String url,
			@JsonProperty("title") String title) {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Usage(
			@JsonProperty("prompt_tokens") Integer promptTokens,
			@JsonProperty("completion_tokens") Integer completionTokens,
			@JsonProperty("total_tokens") Integer totalTokens,
			@JsonProperty("plugins") List<PluginUsage> plugins) {

		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record PluginUsage(
				@JsonProperty("name") String name,
				@JsonProperty("parse_tokens") Integer parseTokens,
				@JsonProperty("abstract_tokens") Integer abstractTokens,
				@JsonProperty("search_tokens") Integer searchTokens,
				@JsonProperty("total_tokens") Integer totalTokens) {

		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ChatCompletionChunk(
			@JsonProperty("id") String id,
			@JsonProperty("object") String object,
			@JsonProperty("created") Long created,
			@JsonProperty("sentence_id") String sentenceId,
			@JsonProperty("is_end") Boolean isEnd,
			@JsonProperty("is_truncated") Boolean isTruncated,
			@JsonProperty("finish_reason") ChatCompletionFinishReason finishReason,
			@JsonProperty("search_info") ChatCompletion.SearchInfo searchInfo,
			@JsonProperty("result") String result,
			@JsonProperty("need_clear_history") Boolean needClearHistory,
			@JsonProperty("flag") Integer flag,
			@JsonProperty("ban_round") Integer banRound,
			@JsonProperty("usage") Usage usage,
			@JsonProperty("function_call") FunctionCall functionCall) {

	}

	// Embedding API
	public <T> ResponseEntity<EmbeddingList<Embedding>> embeddings(EmbeddingRequest<T> embeddingRequest) {

		Assert.notNull(embeddingRequest, "The request body can not be null.");
		Assert.notNull(embeddingRequest.input(), "The input can not be null.");
		Assert.isTrue(embeddingRequest.input() instanceof String || embeddingRequest.input() instanceof List,
				"The input must be either a String, or a List of Strings or List of List of integers.");

		if (embeddingRequest.input() instanceof List list) {
			Assert.isTrue(!CollectionUtils.isEmpty(list), "The input list can not be empty.");
			Assert.isTrue(list.size() <= 2048, "The list must be dimensions or less");
			Assert.isTrue(list.get(0) instanceof String || list.get(0) instanceof Integer
					|| list.get(0) instanceof List,
					"The input must be either a String, or a list of Strings or list of integers.");
		}

		Instant timestamp = Instant.now();
		String authorization = ApiUtils.generationAuthorization(accessKey, secretKey, timestamp,
				embeddingRequest.model(), ApiUtils.DEFAULT_BASE_EMBEDDING_URI);

		return this.restClient.post()
				.uri(ApiUtils.DEFAULT_BASE_EMBEDDING_URI + embeddingRequest.model())
				.headers(headers -> {
					headers.set("x-bce-date", ApiUtils.formatDate(timestamp));
					headers.set("Authorization", authorization);
				})
				.body(embeddingRequest)
				.retrieve()
				.toEntity(new ParameterizedTypeReference<>() {
				});
	}

	public enum EmbeddingModel {
		Embedding_V1("embedding-v1"),
		BGE_LARGE_ZH("bge_large_zh"),
		BGE_LARGE_EN("bge_large_en"),
		TAO_8K("tao_8k");

		public final String value;

		EmbeddingModel(String value) {
			this.value = value;
		}

		public String getValue() {
			return value;
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Embedding(
			@JsonProperty("index") Integer index,
			@JsonProperty("embedding") List<Double> embedding,
			@JsonProperty("object") String object) {

		public Embedding(Integer index, List<Double> embedding) {
			this(index, embedding, "embedding");
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EmbeddingRequest<T>(
			@JsonProperty("input") T input,
			@JsonProperty("model") String model,
			@JsonProperty("user_id") String userId) {

		public EmbeddingRequest(T input, String model) {
			this(input, model, null);
		}

		public EmbeddingRequest(T input) {
			this(input, DEFAULT_EMBEDDING_MODEL);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record EmbeddingList<T>(
			@JsonProperty("id") String id,
			@JsonProperty("object") String object,
			@JsonProperty("created") Long created,
			@JsonProperty("data") List<T> data,
			@JsonProperty("usage") Usage usage) {

		@JsonInclude(JsonInclude.Include.NON_NULL)
		public record Usage(
				@JsonProperty("prompt_tokens") Integer promptTokens,
				@JsonProperty("total_tokens") Integer totalTokens) {

		}
	}
	// @formatter:on

}

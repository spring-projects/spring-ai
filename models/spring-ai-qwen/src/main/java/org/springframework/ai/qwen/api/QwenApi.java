package org.springframework.ai.qwen.api;

import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.protocol.Protocol;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.UsageUtils;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.qwen.QwenChatOptions;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.springframework.ai.qwen.api.QwenApiHelper.toQwenSearchInfo;
import static org.springframework.ai.qwen.api.QwenApiHelper.defaultUsageFrom;
import static org.springframework.ai.qwen.api.QwenApiHelper.generationsFrom;
import static org.springframework.ai.qwen.api.QwenApiHelper.getOrDefault;
import static org.springframework.ai.qwen.api.QwenApiHelper.isMultimodalModelName;
import static org.springframework.ai.qwen.api.QwenApiHelper.isStreamingDone;
import static org.springframework.ai.qwen.api.QwenApiHelper.isStreamingToolCall;
import static org.springframework.ai.qwen.api.QwenApiHelper.isSupportingIncrementalOutputModelName;
import static org.springframework.ai.qwen.api.QwenApiHelper.newGenerationResult;
import static org.springframework.ai.qwen.api.QwenApiHelper.toGenerationParam;
import static org.springframework.ai.qwen.api.QwenApiHelper.toMultiModalConversationParam;
import static org.springframework.ai.qwen.api.QwenApiHelper.toQwenResultCallback;

public class QwenApi {

	private final String apiKey;

	private final com.alibaba.dashscope.aigc.generation.Generation generation;

	private final com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation conv;

	/**
	 * Some models support deeply customized parameters. Here is a way to intervene in the
	 * request parameters of the qwen models at runtime.
	 */
	private Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer = p -> {
	};

	/**
	 * Some models support deeply customized parameters. Here is a way to intervene in the
	 * request parameters of the qwen multimodal-models at runtime.
	 */
	private Consumer<MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?>> multimodalConversationParamCustomizer = p -> {
	};

	public QwenApi(String baseUrl, ApiKey apiKey) {
		if (!StringUtils.hasText(baseUrl)) {
			this.conv = new MultiModalConversation();
			this.generation = new com.alibaba.dashscope.aigc.generation.Generation();
		}
		else if (baseUrl.startsWith("wss://")) {
			this.conv = new MultiModalConversation(Protocol.WEBSOCKET.getValue(), baseUrl);
			this.generation = new com.alibaba.dashscope.aigc.generation.Generation(Protocol.WEBSOCKET.getValue(),
					baseUrl);
		}
		else {
			this.conv = new MultiModalConversation(Protocol.HTTP.getValue(), baseUrl);
			this.generation = new com.alibaba.dashscope.aigc.generation.Generation(Protocol.HTTP.getValue(), baseUrl);
		}

		this.apiKey = apiKey.getValue();
	}

	public static Builder builder() {
		return new Builder();
	}

	public ChatResponse call(Prompt prompt, ChatResponse previousChatResponse) {
		return isMultimodalModel(prompt) ? callMultimodalModel(prompt, previousChatResponse)
				: callNonMultimodalModel(prompt, previousChatResponse);
	}

	private ChatResponse callNonMultimodalModel(Prompt prompt, ChatResponse previousChatResponse) {
		GenerationParam param = toGenerationParam(apiKey, prompt, false, generationParamCustomizer);

		try {
			GenerationResult result = generation.call(param);
			List<Generation> generations = generationsFrom(result);
			Usage currentUsage = defaultUsageFrom(result.getUsage());
			Usage accumulatedUsage = UsageUtils.getCumulativeUsage(currentUsage, previousChatResponse);
			ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder()
				.id(result.getRequestId())
				.usage(accumulatedUsage)
				.model(prompt.getOptions().getModel());
			if (result.getOutput().getSearchInfo() != null) {
				metadataBuilder.keyValue("searchInfo", toQwenSearchInfo(result.getOutput().getSearchInfo()));
			}
			return new ChatResponse(generations, metadataBuilder.build());
		}
		catch (NoApiKeyException | InputRequiredException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private ChatResponse callMultimodalModel(Prompt prompt, ChatResponse previousChatResponse) {
		MultiModalConversationParam param = toMultiModalConversationParam(apiKey, prompt, false,
				multimodalConversationParamCustomizer);

		try {
			MultiModalConversationResult result = conv.call(param);
			List<Generation> generations = generationsFrom(result);
			Usage currentUsage = defaultUsageFrom(result.getUsage());
			Usage accumulatedUsage = UsageUtils.getCumulativeUsage(currentUsage, previousChatResponse);
			ChatResponseMetadata metadata = ChatResponseMetadata.builder()
				.id(result.getRequestId())
				.usage(accumulatedUsage)
				.model(prompt.getOptions().getModel())
				.build();
			return new ChatResponse(generations, metadata);
		}
		catch (NoApiKeyException e) {
			throw new IllegalArgumentException(e);
		}
		catch (UploadFileException e) {
			throw new IllegalStateException(e);
		}
	}

	public Flux<ChatResponse> streamCall(Prompt prompt, ChatResponse previousChatResponse) {
		return isMultimodalModel(prompt) ? streamCallMultimodalModel(prompt, previousChatResponse)
				: streamCallNonMultimodalModel(prompt, previousChatResponse);
	}

	private Flux<ChatResponse> streamCallNonMultimodalModel(Prompt prompt, ChatResponse previousChatResponse) {
		boolean incrementalOutput = supportIncrementalOutput(prompt);
		GenerationParam param = toGenerationParam(apiKey, prompt, incrementalOutput, generationParamCustomizer);
		StringBuilder generatedContent = new StringBuilder();
		Sinks.Many<GenerationResult> sink = Sinks.many().multicast().onBackpressureBuffer();
		AtomicBoolean isInsideTool = new AtomicBoolean(false);

		try {
			generation.streamCall(param, toQwenResultCallback(sink));

			return sink.asFlux().map(result -> {
				if (isStreamingToolCall(result)) {
					isInsideTool.set(true);
				}
				if (!incrementalOutput) {
					// unified into incremental output mode
					Optional.of(result)
						.map(GenerationResult::getOutput)
						.map(GenerationOutput::getChoices)
						.filter(choices -> !choices.isEmpty())
						.map(choices -> choices.get(0))
						.map(GenerationOutput.Choice::getMessage)
						.filter(message -> StringUtils.hasText(message.getContent()))
						.ifPresent(message -> {
							String partialContent = message.getContent().substring(generatedContent.length());
							generatedContent.append(partialContent);
							message.setContent(partialContent);
						});
				}
				return result;
			}).windowUntil(result -> {
				if (isInsideTool.get() && isStreamingDone(result)) {
					isInsideTool.set(false);
					return true;
				}
				return !isInsideTool.get();
			}).concatMapIterable(window -> {
				Mono<GenerationResult> monoChunk = window.reduce(newGenerationResult(), QwenApiHelper::mergeResult);
				return List.of(monoChunk);
			}).flatMap(mono -> mono).map(result -> {
				List<Generation> generations = generationsFrom(result);
				Usage currentUsage = defaultUsageFrom(result.getUsage());
				Usage accumulatedUsage = UsageUtils.getCumulativeUsage(currentUsage, previousChatResponse);
				ChatResponseMetadata.Builder metadataBuilder = ChatResponseMetadata.builder()
					.id(result.getRequestId())
					.usage(accumulatedUsage)
					.model(prompt.getOptions().getModel());
				if (result.getOutput().getSearchInfo() != null) {
					metadataBuilder.keyValue("searchInfo", toQwenSearchInfo(result.getOutput().getSearchInfo()));
				}
				return new ChatResponse(generations, metadataBuilder.build());
			});

		}
		catch (NoApiKeyException | InputRequiredException e) {
			throw new IllegalArgumentException(e);
		}
	}

	private Flux<ChatResponse> streamCallMultimodalModel(Prompt prompt, ChatResponse previousChatResponse) {
		boolean incrementalOutput = supportIncrementalOutput(prompt);
		MultiModalConversationParam param = toMultiModalConversationParam(apiKey, prompt, incrementalOutput,
				multimodalConversationParamCustomizer);

		StringBuilder generatedContent = new StringBuilder();
		Sinks.Many<MultiModalConversationResult> sink = Sinks.many().multicast().onBackpressureBuffer();

		try {
			// note: multimodal models do not support toolcalls
			conv.streamCall(param, toQwenResultCallback(sink));

			return sink.asFlux().map(result -> {
				if (!incrementalOutput) {
					// unified into incremental output mode
					Optional.of(result)
						.map(MultiModalConversationResult::getOutput)
						.map(MultiModalConversationOutput::getChoices)
						.filter(choices -> !choices.isEmpty())
						.map(choices -> choices.get(0))
						.map(MultiModalConversationOutput.Choice::getMessage)
						.map(MultiModalMessage::getContent)
						.filter(contents -> !contents.isEmpty())
						.map(contents -> contents.get(0))
						.filter(content -> StringUtils.hasText((String) content.get("text")))
						.ifPresent(content -> {
							String textContent = (String) content.get("text");
							String partialContent = textContent.substring(generatedContent.length());
							generatedContent.append(partialContent);
							content.put("text", partialContent);
						});
				}
				return result;
			}).map(result -> {
				List<Generation> generations = generationsFrom(result);
				Usage currentUsage = defaultUsageFrom(result.getUsage());
				Usage accumulatedUsage = UsageUtils.getCumulativeUsage(currentUsage, previousChatResponse);
				ChatResponseMetadata metadata = ChatResponseMetadata.builder()
					.id(result.getRequestId())
					.usage(accumulatedUsage)
					.model(prompt.getOptions().getModel())
					.build();
				return new ChatResponse(generations, metadata);
			});

		}
		catch (NoApiKeyException | InputRequiredException e) {
			throw new IllegalArgumentException(e);
		}
		catch (UploadFileException e) {
			throw new IllegalStateException(e);
		}
	}

	boolean isMultimodalModel(Prompt prompt) {
		ChatOptions options = prompt.getOptions();
		if (!(options instanceof QwenChatOptions)) {
			throw new IllegalArgumentException("options should be an instance of QwenChatOption");
		}

		String modelName = options.getModel();
		Boolean isMultimodalModel = ((QwenChatOptions) options).isMultimodalModel();
		isMultimodalModel = getOrDefault(isMultimodalModel, isMultimodalModelName(modelName));

		return Boolean.TRUE.equals(isMultimodalModel);
	}

	boolean supportIncrementalOutput(Prompt prompt) {
		ChatOptions options = prompt.getOptions();
		if (!(options instanceof QwenChatOptions)) {
			throw new IllegalArgumentException("options should be an instance of QwenChatOption");
		}

		String modelName = options.getModel();
		Boolean supportIncrementalOutput = ((QwenChatOptions) options).getSupportIncrementalOutput();
		supportIncrementalOutput = getOrDefault(supportIncrementalOutput,
				isSupportingIncrementalOutputModelName(modelName));

		return Boolean.TRUE.equals(supportIncrementalOutput);
	}

	public void setGenerationParamCustomizer(
			Consumer<GenerationParam.GenerationParamBuilder<?, ?>> generationParamCustomizer) {
		this.generationParamCustomizer = generationParamCustomizer;
	}

	public void setMultimodalConversationParamCustomizer(
			Consumer<MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?>> multimodalConversationParamCustomizer) {
		this.multimodalConversationParamCustomizer = multimodalConversationParamCustomizer;
	}

	public static class Builder {

		private String baseUrl;

		private ApiKey apiKey;

		public Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(ApiKey apiKey) {
			Assert.notNull(apiKey, "apiKey cannot be null");
			this.apiKey = apiKey;
			return this;
		}

		public Builder apiKey(String simpleApiKey) {
			Assert.notNull(simpleApiKey, "simpleApiKey cannot be null");
			this.apiKey = new SimpleApiKey(simpleApiKey);
			return this;
		}

		public QwenApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new QwenApi(this.baseUrl, this.apiKey);
		}

	}

}

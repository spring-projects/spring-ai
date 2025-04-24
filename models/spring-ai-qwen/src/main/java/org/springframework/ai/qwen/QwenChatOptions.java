package org.springframework.ai.qwen;

import com.alibaba.dashscope.common.ResponseFormat;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.ai.qwen.api.QwenApiHelper.copyIfNotNull;
import static org.springframework.ai.qwen.api.QwenApiHelper.getOrDefault;

/**
 * Options for the OpenAI Chat API.
 *
 * @author Peng Jiang
 * @since 1.0.0
 */
@SuppressWarnings("LombokGetterMayBeUsed")
public class QwenChatOptions implements ToolCallingChatOptions {

	/**
	 * ID of the model to use.
	 */
	private String model;

	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on their
	 * existing frequency in the text so far, decreasing the model's likelihood to repeat
	 * the same line verbatim.
	 */
	private Double frequencyPenalty;

	/**
	 * The maximum number of tokens to generate in the chat completion. The total length
	 * of input tokens and generated tokens is limited by the model's context length.
	 */
	private Integer maxTokens;

	/**
	 * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether
	 * they appear in the text so far, increasing the model's likelihood to talk about new
	 * topics.
	 */
	private Double presencePenalty;

	/**
	 * An object specifying the format that the model must output. Setting to { "type":
	 * "json_object" } enables JSON mode, which guarantees the message the model generates
	 * is valid JSON.
	 */
	private ResponseFormat responseFormat;

	/**
	 * If specified, our system will make a best effort to sample deterministically, such
	 * that repeated requests with the same seed and parameters should return the same
	 * result.
	 */
	private Integer seed;

	/**
	 * Up to 4 sequences where the API will stop generating further tokens.
	 */
	private List<String> stopSequences;

	/**
	 * What sampling temperature to use, between 0 and 1. Higher values like 0.8 will make
	 * the output more random, while lower values like 0.2 will make it more focused and
	 * deterministic. We generally recommend altering this or top_p but not both.
	 */
	private Double temperature;

	/**
	 * An alternative to sampling with temperature, called nucleus sampling, where the
	 * model considers the results of the tokens with top_p probability mass. So 0.1 means
	 * only the tokens comprising the top 10% probability mass are considered. We
	 * generally recommend altering this or temperature but not both.
	 */
	private Double topP;

	/**
	 * The size of the candidate set for sampling during the generation process. For
	 * example, when the value is 50, only the 50 tokens with the highest scores in a
	 * single generation will form the candidate set for random sampling. The larger the
	 * value, the higher the randomness of the generation; the smaller the value,the
	 * higher the certainty of the generation. When the value is None or when top_k is
	 * greater than 100, it means that the top_k strategy is not enabled, and only the
	 * top_p strategy is effective. The value needs to be greater than or equal to 0.
	 */
	private Integer topK;

	/**
	 * Collection of {@link ToolCallback}s to be used for tool calling in the chat
	 * completion requests.
	 */
	private List<ToolCallback> toolCallbacks;

	/**
	 * Collection of tool names to be resolved at runtime and used for tool calling in the
	 * chat completion requests.
	 */
	private Set<String> toolNames;

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	private Boolean internalToolExecutionEnabled;

	private Map<String, Object> toolContext;

	/**
	 * Controls which (if any) function is called by the model. none means the model will
	 * not call a function and instead generates a message. auto means the model can pick
	 * between generating a message or calling a function. Specifying a particular
	 * function via {"type: "function", "function": {"name": "my_function"}} forces the
	 * model to call that function. none is the default when no functions are present.
	 * auto is the default if functions are present.
	 */
	private Object toolChoice;

	/**
	 * Whether the model should use internet search results for reference when generating
	 * text.
	 */
	private Boolean enableSearch;

	/**
	 * The strategy for network search. Only takes effect when enableSearch is true.
	 */
	private SearchOptions searchOptions;

	/**
	 * The translation parameters you need to configure when you use the translation
	 * models.
	 */
	private TranslationOptions translationOptions;

	/**
	 * Whether to increase the default token limit for input images. The default token
	 * limit for input images is 1280. When configured to true, the token limit for input
	 * images is 16384. Default value is false.
	 */
	private Boolean vlHighResolutionImages;

	/**
	 * Whether the model is a multimodal model (whether it supports multimodal input). If
	 * not specified, it will be judged based on the model name when called, but these
	 * judgments may not keep up with the latest situation.
	 */
	private Boolean isMultimodalModel;

	/**
	 * Whether the model supports incremental output in the streaming output mode. This
	 * parameter is used to assist QwenChatModel in providing incremental output in stream
	 * mode. If not specified, it will be judged based on the model name when called, but
	 * these judgments may not keep up with the latest situation.
	 */
	private Boolean supportIncrementalOutput;

	/**
	 * User-defined parameters. They may have special effects on some special models.
	 */
	private Map<String, Object> custom;

	private QwenChatOptions(Builder builder) {
		this.model = builder.model;
		this.frequencyPenalty = builder.frequencyPenalty;
		this.maxTokens = builder.maxTokens;
		this.presencePenalty = builder.presencePenalty;
		this.responseFormat = builder.responseFormat;
		this.seed = builder.seed;
		this.stopSequences = builder.stopSequences;
		this.temperature = builder.temperature;
		this.topP = builder.topP;
		this.topK = builder.topK;
		this.toolCallbacks = builder.toolCallbacks;
		this.toolNames = builder.toolNames;
		this.internalToolExecutionEnabled = builder.internalToolExecutionEnabled;
		this.toolContext = builder.toolContext;
		this.toolChoice = builder.toolChoice;
		this.enableSearch = builder.enableSearch;
		this.searchOptions = builder.searchOptions;
		this.translationOptions = builder.translationOptions;
		this.vlHighResolutionImages = builder.vlHighResolutionImages;
		this.isMultimodalModel = builder.isMultimodalModel;
		this.supportIncrementalOutput = builder.supportIncrementalOutput;
		this.custom = builder.custom;
	}

	@Override
	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Double getFrequencyPenalty() {
		return frequencyPenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	@Override
	public Integer getMaxTokens() {
		return maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	@Override
	public Double getPresencePenalty() {
		return presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public ResponseFormat getResponseFormat() {
		return responseFormat;
	}

	public void setResponseFormat(ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public Integer getSeed() {
		return seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	public List<String> getStopSequences() {
		return stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public Double getTemperature() {
		return temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public Double getTopP() {
		return topP;
	}

	@Override
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = toolCallbacks;
	}

	@Override
	public Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(tool -> Assert.hasText(tool, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
	}

	@Override
	@Nullable
	public Boolean getInternalToolExecutionEnabled() {
		return internalToolExecutionEnabled;
	}

	@Override
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	@Override
	public Integer getTopK() {
		return topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	@Override
	public Map<String, Object> getToolContext() {
		return toolContext;
	}

	@Override
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	public Object getToolChoice() {
		return toolChoice;
	}

	public void setToolChoice(Object toolChoice) {
		this.toolChoice = toolChoice;
	}

	public Boolean isEnableSearch() {
		return enableSearch;
	}

	public void setEnableSearch(Boolean enableSearch) {
		this.enableSearch = enableSearch;
	}

	public SearchOptions getSearchOptions() {
		return searchOptions;
	}

	public void setSearchOptions(SearchOptions searchOptions) {
		this.searchOptions = searchOptions;
	}

	public TranslationOptions getTranslationOptions() {
		return translationOptions;
	}

	public void setTranslationOptions(TranslationOptions translationOptions) {
		this.translationOptions = translationOptions;
	}

	public Boolean getVlHighResolutionImages() {
		return vlHighResolutionImages;
	}

	public void setVlHighResolutionImages(Boolean vlHighResolutionImages) {
		this.vlHighResolutionImages = vlHighResolutionImages;
	}

	public Boolean getIsMultimodalModel() {
		return isMultimodalModel;
	}

	public void setIsMultimodalModel(Boolean isMultimodalModel) {
		this.isMultimodalModel = isMultimodalModel;
	}

	public Boolean getSupportIncrementalOutput() {
		return supportIncrementalOutput;
	}

	public void setSupportIncrementalOutput(Boolean supportIncrementalOutput) {
		this.supportIncrementalOutput = supportIncrementalOutput;
	}

	public Map<String, Object> getCustom() {
		return custom;
	}

	public void setCustom(Map<String, Object> custom) {
		this.custom = custom;
	}

	@Override
	public QwenChatOptions copy() {
		return fromOptions(this);
	}

	public static QwenChatOptions fromOptions(QwenChatOptions fromOptions) {
		return QwenChatOptions.builder().overrideWith(fromOptions).build();
	}

	public QwenChatOptions overrideWith(QwenChatOptions that) {
		return QwenChatOptions.builder().overrideWith(this).overrideWith(that).build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String model;

		private Double frequencyPenalty;

		private Integer maxTokens;

		private Double presencePenalty;

		private ResponseFormat responseFormat;

		private Integer seed;

		private List<String> stopSequences = new ArrayList<>();

		private Double temperature;

		private Double topP;

		private Integer topK;

		private List<ToolCallback> toolCallbacks = new ArrayList<>();

		private Set<String> toolNames = new HashSet<>();

		private Boolean internalToolExecutionEnabled;

		private Map<String, Object> toolContext = new HashMap<>();

		private Object toolChoice;

		private Boolean enableSearch;

		private SearchOptions searchOptions;

		private TranslationOptions translationOptions;

		private Boolean vlHighResolutionImages;

		private Boolean isMultimodalModel;

		private Boolean supportIncrementalOutput;

		private Map<String, Object> custom;

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.presencePenalty = presencePenalty;
			return this;
		}

		public Builder responseFormat(ResponseFormat responseFormat) {
			this.responseFormat = responseFormat;
			return this;
		}

		public Builder seed(Integer seed) {
			this.seed = seed;
			return this;
		}

		public Builder stopSequences(List<String> stopSequences) {
			this.stopSequences = stopSequences;
			return this;
		}

		public Builder temperature(Double temperature) {
			this.temperature = temperature;
			return this;
		}

		public Builder topP(Double topP) {
			this.topP = topP;
			return this;
		}

		public Builder topK(Integer topK) {
			this.topK = topK;
			return this;
		}

		public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.toolCallbacks = toolCallbacks;
			return this;
		}

		public Builder toolNames(Set<String> toolNames) {
			this.toolNames = toolNames;
			return this;
		}

		public Builder internalToolExecutionEnabled(Boolean enabled) {
			this.internalToolExecutionEnabled = enabled;
			return this;
		}

		public Builder toolContext(Map<String, Object> toolContext) {
			this.toolContext = toolContext;
			return this;
		}

		public Builder toolChoice(Object toolChoice) {
			this.toolChoice = toolChoice;
			return this;
		}

		public Builder enableSearch(Boolean enableSearch) {
			this.enableSearch = enableSearch;
			return this;
		}

		public Builder searchOptions(SearchOptions searchOptions) {
			this.searchOptions = searchOptions;
			return this;
		}

		public Builder translationOptions(TranslationOptions translationOptions) {
			this.translationOptions = translationOptions;
			return this;
		}

		public Builder vlHighResolutionImages(Boolean vlHighResolutionImages) {
			this.vlHighResolutionImages = vlHighResolutionImages;
			return this;
		}

		public Builder isMultimodalModel(Boolean isMultimodalModel) {
			this.isMultimodalModel = isMultimodalModel;
			return this;
		}

		public Builder supportIncrementalOutput(Boolean supportIncrementalOutput) {
			this.supportIncrementalOutput = supportIncrementalOutput;
			return this;
		}

		public Builder custom(Map<String, Object> custom) {
			this.custom = custom;
			return this;
		}

		public Builder overrideWith(QwenChatOptions fromOptions) {
			if (fromOptions == null) {
				return this;
			}

			this.model(getOrDefault(fromOptions.getModel(), this.model));
			this.frequencyPenalty(getOrDefault(fromOptions.getFrequencyPenalty(), this.frequencyPenalty));
			this.maxTokens(getOrDefault(fromOptions.getMaxTokens(), this.maxTokens));
			this.presencePenalty(getOrDefault(fromOptions.getPresencePenalty(), this.presencePenalty));
			this.responseFormat(getOrDefault(fromOptions.getResponseFormat(), this.responseFormat));
			this.seed(getOrDefault(fromOptions.getSeed(), this.seed));
			this.stopSequences(copyIfNotNull(getOrDefault(fromOptions.getStopSequences(), this.stopSequences)));
			this.temperature(getOrDefault(fromOptions.getTemperature(), this.temperature));
			this.topP(getOrDefault(fromOptions.getTopP(), this.topP));
			this.topK(getOrDefault(fromOptions.getTopK(), this.topK));
			this.toolCallbacks(copyIfNotNull(getOrDefault(fromOptions.getToolCallbacks(), this.toolCallbacks)));
			this.toolNames(copyIfNotNull(getOrDefault(fromOptions.getToolNames(), this.toolNames)));
			this.internalToolExecutionEnabled(
					getOrDefault(fromOptions.isInternalToolExecutionEnabled(), this.internalToolExecutionEnabled));
			this.toolContext(getOrDefault(fromOptions.getToolContext(), this.toolContext));
			this.toolChoice(getOrDefault(fromOptions.getToolChoice(), this.toolChoice));
			this.enableSearch(getOrDefault(fromOptions.isEnableSearch(), this.enableSearch));
			this.searchOptions(getOrDefault(fromOptions.getSearchOptions(), this.searchOptions));
			this.translationOptions(getOrDefault(fromOptions.getTranslationOptions(), this.translationOptions));
			this.vlHighResolutionImages(
					getOrDefault(fromOptions.getVlHighResolutionImages(), this.vlHighResolutionImages));
			this.isMultimodalModel(getOrDefault(fromOptions.getIsMultimodalModel(), this.isMultimodalModel));
			this.supportIncrementalOutput(
					getOrDefault(fromOptions.getSupportIncrementalOutput(), this.supportIncrementalOutput));
			this.custom(copyIfNotNull(getOrDefault(fromOptions.getCustom(), this.custom)));
			return this;
		}

		public QwenChatOptions build() {
			return new QwenChatOptions(this);
		}

	}

	/**
	 * The strategy for network search.
	 *
	 * @param enableSource Whether to display the searched information in the returned
	 * results. Default value is false.
	 * @param enableCitation Whether to enable the [1] or [ref_1] style superscript
	 * annotation function. This function takes effect only when enable_source is true.
	 * Default value is false.
	 * @param citationFormat Subscript style. Only available when enable_citation is true.
	 * Supported styles: “[<number>]” and “[ref_<number>]”. Default value is “[<number>]”.
	 * @param forcedSearch Whether to force search to start.
	 * @param searchStrategy The amount of Internet information searched. Supported
	 * values: “standard” and “pro”. Default value is “standard”.
	 */
	public record SearchOptions(Boolean enableSource, Boolean enableCitation, String citationFormat,
			Boolean forcedSearch, String searchStrategy) {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private Boolean enableSource;

			private Boolean enableCitation;

			private String citationFormat;

			private Boolean forcedSearch;

			private String searchStrategy;

			public Builder enableSource(Boolean enableSource) {
				this.enableSource = enableSource;
				return this;
			}

			public Builder enableCitation(Boolean enableCitation) {
				this.enableCitation = enableCitation;
				return this;
			}

			public Builder citationFormat(String citationFormat) {
				this.citationFormat = citationFormat;
				return this;
			}

			public Builder forcedSearch(Boolean forcedSearch) {
				this.forcedSearch = forcedSearch;
				return this;
			}

			public Builder searchStrategy(String searchStrategy) {
				this.searchStrategy = searchStrategy;
				return this;
			}

			public SearchOptions build() {
				return new SearchOptions(enableSource, enableCitation, citationFormat, forcedSearch, searchStrategy);
			}

		}
	}

	/**
	 * The translation parameters you need to configure when you use the translation
	 * models.
	 *
	 * @param sourceLang The full English name of the source language.For more
	 * information, see <a href=
	 * "https://www.alibabacloud.com/help/en/model-studio/machine-translation">Supported
	 * Languages</a>. You can set source_lang to "auto" and the model will automatically
	 * determine the language of the input text.
	 * @param targetLang The full English name of the target language.For more
	 * information, see <a href=
	 * "https://www.alibabacloud.com/help/en/model-studio/machine-translation">Supported
	 * Languages</a>.
	 * @param terms An array of terms that needs to be set when using the
	 * term-intervention-translation feature.
	 * @param tmList The translation memory array that needs to be set when using the
	 * translation-memory feature.
	 * @param domains The domain prompt statement needs to be set when using the
	 * domain-prompt feature.
	 */
	public record TranslationOptions(String sourceLang, String targetLang, List<TranslationOptionTerm> terms,
			List<TranslationOptionTerm> tmList, String domains) {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private String sourceLang;

			private String targetLang;

			private List<TranslationOptionTerm> terms;

			private List<TranslationOptionTerm> tmLists;

			private String domains;

			public Builder sourceLang(String sourceLang) {
				this.sourceLang = sourceLang;
				return this;
			}

			public Builder targetLang(String targetLang) {
				this.targetLang = targetLang;
				return this;
			}

			public Builder terms(List<TranslationOptionTerm> terms) {
				this.terms = terms;
				return this;
			}

			public Builder tmLists(List<TranslationOptionTerm> tmLists) {
				this.tmLists = tmLists;
				return this;
			}

			public Builder domains(String domains) {
				this.domains = domains;
				return this;
			}

			public TranslationOptions build() {
				return new TranslationOptions(sourceLang, targetLang, terms, tmLists, domains);
			}

		}
	}

	/**
	 * @param source The term in the source language.
	 * @param target The term in the target language.
	 */
	public record TranslationOptionTerm(String source, String target) {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private String source;

			private String target;

			public Builder source(String source) {
				this.source = source;
				return this;
			}

			public Builder target(String target) {
				this.target = target;
				return this;
			}

			public TranslationOptionTerm build() {
				return new TranslationOptionTerm(source, target);
			}

		}
	}

}

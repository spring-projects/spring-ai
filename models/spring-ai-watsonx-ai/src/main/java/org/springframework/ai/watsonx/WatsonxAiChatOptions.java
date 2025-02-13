/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.watsonx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.prompt.AbstractChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Helper class for creating watsonx.ai options.
 *
 * @author Pablo Sanchidrian Herrera
 * @author John Jairo Moreno Rojas
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @since 1.0.0
 * @see <a href=
 * "https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-model-parameters.html?context=wx&audience=wdp">watsonx.ai
 * valid Parameters and values</a>
 */
// @formatter:off

public class WatsonxAiChatOptions extends AbstractChatOptions implements ChatOptions {

	@JsonIgnore
	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Decoding is the process that a model uses to choose the tokens in the generated output.
	 * Choose one of the following decoding options:
	 *
	 * Greedy: Selects the token with the highest probability at each step of the decoding process.
	 * Greedy decoding produces output that closely matches the most common language in the model's pretraining
	 * data and in your prompt text, which is desirable in less creative or fact-based use cases. A weakness of
	 * greedy decoding is that it can cause repetitive loops in the generated output.
	 *
	 * Sampling decoding: Offers more variability in how tokens are selected.
	 * With sampling decoding, the model samples tokens, meaning the model chooses a subset of tokens,
	 * and then one token is chosen randomly from this subset to be added to the output text. Sampling adds
	 * variability and randomness to the decoding process, which can be desirable in creative use cases.
	 * However, with greater variability comes a greater risk of incorrect or nonsensical output.
	 * (Default: greedy)
	 */
	@JsonProperty("decoding_method")
	private String decodingMethod;

	/**
	 * Sets the limit of tokens that the LLM follow. (Default: 20)
	 */
	@JsonProperty("max_new_tokens")
	private Integer maxNewTokens;

	/**
	 * Sets how many tokens must the LLM generate. (Default: 0)
	 */
	@JsonProperty("min_new_tokens")
	private Integer minNewTokens;

	/**
	 * Sets how strongly to penalize repetitions. A higher value
	 * (e.g., 1.8) will penalize repetitions more strongly, while a lower value (e.g.,
	 * 1.1) will be more lenient. (Default: 1.0)
	 */
	@JsonProperty("repetition_penalty")
	private Double repetitionPenalty;

	/**
	 * Produce repeatable results, set the same random seed value every time. (Default: randomly generated)
	 */
	@JsonProperty("random_seed")
	private Integer randomSeed;

	/**
	 * Set additional request params (some model have non-predefined options)
	 */
	@JsonProperty("additional")
	private Map<String, Object> additional = new HashMap<>();

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Filter out the non-supported fields from the options.
	 * @param options The options to filter.
	 * @return The filtered options.
	 */
	public static Map<String, Object> filterNonSupportedFields(Map<String, Object> options) {
		return options.entrySet().stream()
				.filter(e -> !e.getKey().equals("model"))
				.filter(e -> e.getValue() != null)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	public static WatsonxAiChatOptions fromOptions(WatsonxAiChatOptions fromOptions) {
		return WatsonxAiChatOptions.builder()
				.temperature(fromOptions.getTemperature())
				.topP(fromOptions.getTopP())
				.topK(fromOptions.getTopK())
				.decodingMethod(fromOptions.getDecodingMethod())
				.maxNewTokens(fromOptions.getMaxNewTokens())
				.minNewTokens(fromOptions.getMinNewTokens())
				.stopSequences(fromOptions.getStopSequences() != null ? new ArrayList<>(fromOptions.getStopSequences()) : null)
				.repetitionPenalty(fromOptions.getRepetitionPenalty())
				.randomSeed(fromOptions.getRandomSeed())
				.model(fromOptions.getModel())
				.additionalProperties(fromOptions.getAdditionalProperties() != null ? new HashMap<>(fromOptions.getAdditionalProperties()) : null)
				.build();
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	public String getDecodingMethod() {
		return this.decodingMethod;
	}

	public void setDecodingMethod(String decodingMethod) {
		this.decodingMethod = decodingMethod;
	}

	@Override
	@JsonIgnore
	public Integer getMaxTokens() {
		return getMaxNewTokens();
	}

	@JsonIgnore
	public void setMaxTokens(Integer maxTokens) {
		setMaxNewTokens(maxTokens);
	}

	public Integer getMaxNewTokens() {
		return this.maxNewTokens;
	}

	public void setMaxNewTokens(Integer maxNewTokens) {
		this.maxNewTokens = maxNewTokens;
	}

	public Integer getMinNewTokens() {
		return this.minNewTokens;
	}

	public void setMinNewTokens(Integer minNewTokens) {
		this.minNewTokens = minNewTokens;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	@JsonIgnore
	public Double getPresencePenalty() {
		return getRepetitionPenalty();
	}

	@JsonIgnore
	public void setPresencePenalty(Double presencePenalty) {
		setRepetitionPenalty(presencePenalty);
	}

	public Double getRepetitionPenalty() {
		return this.repetitionPenalty;
	}

	public void setRepetitionPenalty(Double repetitionPenalty) {
		this.repetitionPenalty = repetitionPenalty;
	}

	public Integer getRandomSeed() {
		return this.randomSeed;
	}

	public void setRandomSeed(Integer randomSeed) {
		this.randomSeed = randomSeed;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@JsonAnyGetter
	public Map<String, Object> getAdditionalProperties() {
		return this.additional.entrySet().stream()
				.collect(Collectors.toMap(
						entry -> toSnakeCase(entry.getKey()),
						Map.Entry::getValue
				));
	}

	@JsonAnySetter
	public void addAdditionalProperty(String key, Object value) {
		this.additional.put(key, value);
	}

	@Override
	@JsonIgnore
	public Double getFrequencyPenalty() {
		return null;
	}

	/**
	 * Convert the {@link WatsonxAiChatOptions} object to a {@link Map} of key/value pairs.
	 * @return The {@link Map} of key/value pairs.
	 */
	public Map<String, Object> toMap() {
		try {
			var json = this.mapper.writeValueAsString(this);
			var map = this.mapper.readValue(json, new TypeReference<Map<String, Object>>() { });
			map.remove("additional");

			return map;
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private String toSnakeCase(String input) {
		return input != null ? input.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase() : null;
	}

	@Override
	@SuppressWarnings("unchecked")
	public WatsonxAiChatOptions copy() {
		return fromOptions(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		WatsonxAiChatOptions that = (WatsonxAiChatOptions) o;

		if (decodingMethod != null ? !decodingMethod.equals(that.decodingMethod) : that.decodingMethod != null) return false;
		if (maxNewTokens != null ? !maxNewTokens.equals(that.maxNewTokens) : that.maxNewTokens != null) return false;
		if (minNewTokens != null ? !minNewTokens.equals(that.minNewTokens) : that.minNewTokens != null) return false;
		if (repetitionPenalty != null ? !repetitionPenalty.equals(that.repetitionPenalty) : that.repetitionPenalty != null) return false;
		if (randomSeed != null ? !randomSeed.equals(that.randomSeed) : that.randomSeed != null) return false;
		if (temperature != null ? !temperature.equals(that.temperature) : that.temperature != null) return false;
		if (topP != null ? !topP.equals(that.topP) : that.topP != null) return false;
		if (topK != null ? !topK.equals(that.topK) : that.topK != null) return false;
		if (stopSequences != null ? !stopSequences.equals(that.stopSequences) : that.stopSequences != null) return false;
		if (model != null ? !model.equals(that.model) : that.model != null) return false;
		return additional != null ? additional.equals(that.additional) : that.additional == null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.decodingMethod == null) ? 0 : this.decodingMethod.hashCode());
		result = prime * result + ((this.maxNewTokens == null) ? 0 : this.maxNewTokens.hashCode());
		result = prime * result + ((this.minNewTokens == null) ? 0 : this.minNewTokens.hashCode());
		result = prime * result + ((this.repetitionPenalty == null) ? 0 : this.repetitionPenalty.hashCode());
		result = prime * result + ((this.randomSeed == null) ? 0 : this.randomSeed.hashCode());
		result = prime * result + ((this.temperature == null) ? 0 : this.temperature.hashCode());
		result = prime * result + ((this.topP == null) ? 0 : this.topP.hashCode());
		result = prime * result + ((this.topK == null) ? 0 : this.topK.hashCode());
		result = prime * result + ((this.stopSequences == null) ? 0 : this.stopSequences.hashCode());
		result = prime * result + ((this.model == null) ? 0 : this.model.hashCode());
		result = prime * result + ((this.additional == null) ? 0 : this.additional.hashCode());
		return result;
	}

	public static class Builder {

		WatsonxAiChatOptions options = new WatsonxAiChatOptions();

		public Builder temperature(Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder topP(Double topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder topK(Integer topK) {
			this.options.topK = topK;
			return this;
		}

		public Builder decodingMethod(String decodingMethod) {
			this.options.decodingMethod = decodingMethod;
			return this;
		}

		public Builder maxNewTokens(Integer maxNewTokens) {
			this.options.maxNewTokens = maxNewTokens;
			return this;
		}

		public Builder minNewTokens(Integer minNewTokens) {
			this.options.minNewTokens = minNewTokens;
			return this;
		}

		public Builder stopSequences(List<String> stopSequences) {
			this.options.stopSequences = stopSequences;
			return this;
		}

		public Builder repetitionPenalty(Double repetitionPenalty) {
			this.options.repetitionPenalty = repetitionPenalty;
			return this;
		}

		public Builder randomSeed(Integer randomSeed) {
			this.options.randomSeed = randomSeed;
			return this;
		}

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder additionalProperty(String key, Object value) {
			this.options.additional.put(key, value);
			return this;
		}

		public Builder additionalProperties(Map<String, Object> properties) {
			this.options.additional.putAll(properties);
			return this;
		}

		public WatsonxAiChatOptions build() {
			return this.options;
		}
	}

}
// @formatter:on

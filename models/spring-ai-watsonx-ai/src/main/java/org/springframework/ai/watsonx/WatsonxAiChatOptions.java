/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.watsonx;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Helper class for creating watsonx.ai options.
 *
 * @author Pablo Sanchidrian Herrera
 * @author John Jairo Moreno Rojas
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href=
 * "https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-model-parameters.html?context=wx&audience=wdp">watsonx.ai
 * valid Parameters and values</a>
 */
// @formatter:off

public class WatsonxAiChatOptions implements ChatOptions {

    /**
     * The temperature of the model. Increasing the temperature will
     * make the model answer more creatively. (Default: 0.7)
     */
    @JsonProperty("temperature") private Float temperature;

    /**
     * Works together with top-k. A higher value (e.g., 0.95) will lead to
     * more diverse text, while a lower value (e.g., 0.2) will generate more focused and
     * conservative text. (Default: 1.0)
     */
    @JsonProperty("top_p") private Float topP;

    /**
     * Reduces the probability of generating nonsense. A higher value (e.g.
     * 100) will give more diverse answers, while a lower value (e.g. 10) will be more
     * conservative. (Default: 50)
     */
    @JsonProperty("top_k") private Integer topK;

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
    @JsonProperty("decoding_method") private String decodingMethod;

    /**
     * Sets the limit of tokens that the LLM follow. (Default: 20)
     */
    @JsonProperty("max_new_tokens") private Integer maxNewTokens;

    /**
     * Sets how many tokens must the LLM generate. (Default: 0)
     */
    @JsonProperty("min_new_tokens") private Integer minNewTokens;

    /**
     * Sets when the LLM should stop.
     * (e.g., ["\n\n\n"]) then when the LLM generates three consecutive line breaks it will terminate.
     * Stop sequences are ignored until after the number of tokens that are specified in the Min tokens parameter are generated.
     */
    @JsonProperty("stop_sequences") private List<String> stopSequences;

    /**
     * Sets how strongly to penalize repetitions. A higher value
     * (e.g., 1.8) will penalize repetitions more strongly, while a lower value (e.g.,
     * 1.1) will be more lenient. (Default: 1.0)
     */
    @JsonProperty("repetition_penalty") private Float repetitionPenalty;

    /**
     * Produce repeatable results, set the same random seed value every time. (Default: randomly generated)
     */
    @JsonProperty("random_seed") private Integer randomSeed;

    /**
     * Model is the identifier of the LLM Model to be used
     */
    @JsonProperty("model") private String model;

    /**
     * Set additional request params (some model have non-predefined options)
     */
    @JsonProperty("additional")
    private Map<String, Object> additional = new HashMap<>();

    @JsonIgnore
    private ObjectMapper mapper = new ObjectMapper();

	@Override
    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

	@Override
    public Float getTopP() {
        return topP;
    }

    public void setTopP(Float topP) {
        this.topP = topP;
    }

	@Override
    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public String getDecodingMethod() {
        return decodingMethod;
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
        return maxNewTokens;
    }

    public void setMaxNewTokens(Integer maxNewTokens) {
        this.maxNewTokens = maxNewTokens;
    }

    public Integer getMinNewTokens() {
        return minNewTokens;
    }

    public void setMinNewTokens(Integer minNewTokens) {
        this.minNewTokens = minNewTokens;
    }

	@Override
	public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

	@Override
	@JsonIgnore
	public Float getPresencePenalty() {
    	return getRepetitionPenalty();
    }

	@JsonIgnore
	public void setPresencePenalty(Float presencePenalty) {
		setRepetitionPenalty(presencePenalty);
	}

	public Float getRepetitionPenalty() {
        return repetitionPenalty;
    }

    public void setRepetitionPenalty(Float repetitionPenalty) {
        this.repetitionPenalty = repetitionPenalty;
    }

    public Integer getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(Integer randomSeed) {
        this.randomSeed = randomSeed;
    }

	@Override
    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additional.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> toSnakeCase(entry.getKey()),
                        Map.Entry::getValue
                ));
    }

    @JsonAnySetter
    public void addAdditionalProperty(String key, Object value) {
        additional.put(key, value);
    }

	@Override
	@JsonIgnore
	public Float getFrequencyPenalty() {
    	return null;
    }

	public static Builder builder() {
		return new Builder();
	}

    public static class Builder {

        WatsonxAiChatOptions options = new WatsonxAiChatOptions();

        public Builder withTemperature(Float temperature) {
            this.options.temperature = temperature;
            return this;
        }

        public Builder withTopP(Float topP) {
            this.options.topP = topP;
            return this;
        }

        public Builder withTopK(Integer topK) {
            this.options.topK = topK;
            return this;
        }

        public Builder withDecodingMethod(String decodingMethod) {
            this.options.decodingMethod = decodingMethod;
            return this;
        }

        public Builder withMaxNewTokens(Integer maxNewTokens) {
            this.options.maxNewTokens = maxNewTokens;
            return this;
        }

        public Builder withMinNewTokens(Integer minNewTokens) {
            this.options.minNewTokens = minNewTokens;
            return this;
        }

        public Builder withStopSequences(List<String> stopSequences) {
            this.options.stopSequences = stopSequences;
            return this;
        }

        public Builder withRepetitionPenalty(Float repetitionPenalty) {
            this.options.repetitionPenalty = repetitionPenalty;
            return this;
        }

        public Builder withRandomSeed(Integer randomSeed) {
            this.options.randomSeed = randomSeed;
            return this;
        }

        public Builder withModel(String model) {
            this.options.model = model;
            return this;
        }

        public Builder withAdditionalProperty(String key, Object value) {
            this.options.additional.put(key, value);
            return this;
        }

        public Builder withAdditionalProperties(Map<String, Object> properties) {
            this.options.additional.putAll(properties);
            return this;
        }

        public WatsonxAiChatOptions build() {
            return this.options;
        }
    }

    /**
     * Convert the {@link WatsonxAiChatOptions} object to a {@link Map} of key/value pairs.
     * @return The {@link Map} of key/value pairs.
     */
    public Map<String, Object> toMap() {
        try {
            var json = mapper.writeValueAsString(this);
            var map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            map.remove("additional");

            return map;
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Filter out the non supported fields from the options.
     * @param options The options to filter.
     * @return The filtered options.
     */
    public static Map<String, Object> filterNonSupportedFields(Map<String, Object> options) {
        return options.entrySet().stream()
                .filter(e -> !e.getKey().equals("model"))
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private String toSnakeCase(String input) {
        return input != null ? input.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase() : null;
    }

    @Override
    public WatsonxAiChatOptions copy() {
        return fromOptions(this);
    }

    public static WatsonxAiChatOptions fromOptions(WatsonxAiChatOptions fromOptions) {
        return WatsonxAiChatOptions.builder()
                .withTemperature(fromOptions.getTemperature())
                .withTopP(fromOptions.getTopP())
                .withTopK(fromOptions.getTopK())
                .withDecodingMethod(fromOptions.getDecodingMethod())
                .withMaxNewTokens(fromOptions.getMaxNewTokens())
                .withMinNewTokens(fromOptions.getMinNewTokens())
                .withStopSequences(fromOptions.getStopSequences())
                .withRepetitionPenalty(fromOptions.getRepetitionPenalty())
                .withRandomSeed(fromOptions.getRandomSeed())
                .withModel(fromOptions.getModel())
                .withAdditionalProperties(fromOptions.getAdditionalProperties())
                .build();
    }

}
// @formatter:on
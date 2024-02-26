package org.springframework.ai.watsonx.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Helper class for creating watsonx.ai options.
 *
 * @author Pablo Sanchidrian Herrera
 * @author John Jairo Moreno Rojas
 * @since 0.8.0
 * @see <a href=
 * "https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-model-parameters.html?context=wx&audience=wdp">watsonx.ai
 * valid Parameters and values</a>
 */
// @formatter:off
public class WatsonxAIOptions implements ChatOptions {

    /**
     * The temperature of the model. Increasing the temperature will
     * make the model answer more creatively. (Default: 0.7)
     */
    @JsonProperty("temperature") private Float temperature = 0.7f;

    /**
     * Works together with top-k. A higher value (e.g., 0.95) will lead to
     * more diverse text, while a lower value (e.g., 0.2) will generate more focused and
     * conservative text. (Default: 1.0)
     */
    @JsonProperty("top_p") private Float topP = 1.0f;

    /**
     * Reduces the probability of generating nonsense. A higher value (e.g.
     * 100) will give more diverse answers, while a lower value (e.g. 10) will be more
     * conservative. (Default: 50)
     */
    @JsonProperty("top_k") private Integer topK = 50;

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
    @JsonProperty("decoding_method") private String decodingMethod = "greedy";

    /**
     * Sets the limit of tokens that the LLM follow. (Default: 20)
     */
    @JsonProperty("max_new_tokens") private Integer maxNewTokens = 20;

    /**
     * Sets how many tokens must the LLM generate. (Default: 0)
     */
    @JsonProperty("min_new_tokens") private Integer minNewTokens = 0;

    /**
     * Sets when the LLM should stop.
     * (e.g., ["\n\n\n"]) then when the LLM generates three consecutives line breaks it will terminate.
     * Stop sequences are ignored until after the number of tokens that are specified in the Min tokens parameter are generated.
     */
    @JsonProperty("stop_sequences") private List<String> stopSequences = List.of();

    /**
     * Sets how strongly to penalize repetitions. A higher value
     * (e.g., 1.8) will penalize repetitions more strongly, while a lower value (e.g.,
     * 1.1) will be more lenient. (Default: 1.0)
     */
    @JsonProperty("repetition_penalty") private Float repetitionPenalty = 1.0f;

    /**
     * Produce repeatable results, set the same random seed value every time. (Default: randomly generated)
     */
    @JsonProperty("random_seed") private Integer randomSeed;

    /**
     * Model is the identifier of the LLM Model to be used
     */
    @JsonProperty("model") private String model;

    public WatsonxAIOptions withTemperature(Float temperature) {
        this.temperature = temperature;
        return this;
    }

    public WatsonxAIOptions withTopP(Float topP) {
        this.topP = topP;
        return this;
    }

    public WatsonxAIOptions withTopK(Integer topK) {
        this.topK = topK;
        return this;
    }

    public WatsonxAIOptions withDecodingMethod(String decodingMethod) {
        this.decodingMethod = decodingMethod;
        return this;
    }

    public WatsonxAIOptions withMaxNewTokens(Integer maxNewTokens) {
        this.maxNewTokens = maxNewTokens;
        return this;
    }

    public WatsonxAIOptions withMinNewTokens(Integer minNewTokens) {
        this.minNewTokens = minNewTokens;
        return this;
    }

    public WatsonxAIOptions withStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
        return this;
    }

    public WatsonxAIOptions withRepetitionPenalty(Float repetitionPenalty) {
        this.repetitionPenalty = repetitionPenalty;
        return this;
    }

    public WatsonxAIOptions withRandomSeed(Integer randomSeed) {
        this.randomSeed = randomSeed;
        return this;
    }

    public WatsonxAIOptions withModel(String model) {
        this.model = model;
        return this;
    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public Float getTopP() {
        return topP;
    }

    public void setTopP(Float topP) {
        this.topP = topP;
    }

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

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
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

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    /**
     * Convert the {@link WatsonxAIOptions} object to a {@link Map} of key/value pairs.
     * @return The {@link Map} of key/value pairs.
     */
    public Map<String, Object> toMap() {
        try {
            var json = new ObjectMapper().writeValueAsString(this);
            return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {
            });
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper factory method to create a new {@link WatsonxAIOptions} instance.
     * @return A new {@link WatsonxAIOptions} instance.
     */
    public static WatsonxAIOptions create() {
        return new WatsonxAIOptions();
    }

    /**
     * Filter out the non supported fields from the options.
     * @param options The options to filter.
     * @return The filtered options.
     */
    public static Map<String, Object> filterNonSupportedFields(Map<String, Object> options) {
        return options.entrySet().stream()
                .filter(e -> !e.getKey().equals("model"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


}
// @formatter:on
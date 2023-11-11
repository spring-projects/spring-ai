package org.springframework.experimental.ai.model;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;

public class ClaudeModel extends AbstractAWSBaseModelParams {

    private static final Logger logger = LoggerFactory.getLogger(ClaudeModel.class);

    private List<String> stopSequences = List.of();
    private final ObjectMapper mapper;

    public ClaudeModel(String modelId, ObjectMapper mapper) {
        super(modelId);
        this.mapper = mapper;
    }

    public ClaudeModel(String modelId) {
        super(modelId);
        mapper = new ObjectMapper();
    }

    public List<String> getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    /*
    the json looks like this:
    {
    "prompt": "\n\nHuman:<prompt>\n\nAssistant:",
    "temperature": float,
    "top_p": float,
    "top_k": int,
    "max_tokens_to_sample": int,
    "stop_sequences": ["\n\nHuman:"]
}
     */
    @Override public SdkBytes toPayload(String prompt) {
        var request = new ClaudeModelRequest(prompt, this.getTemperature(), this.getTopP(),
                Double.valueOf(this.getTopK()).intValue(), Double.valueOf(this.getMaxToken()).intValue(),
                stopSequences);
        try {
            return SdkBytes.fromByteArray(mapper.writeValueAsBytes(request));
        } catch (JsonProcessingException e) {
            logger.error("error serializing input to json for params {}", request, e);
            return null;
        }
    }

    // response payload according to https://docs.anthropic.com/claude/reference/complete_post
    @Override public String getResponseContent(SdkBytes response) {
        try {
            JsonNode json = mapper.readValue(response.asByteArray(), JsonNode.class);
            return json.get("completion").asText();
        } catch (IOException e) {
            logger.error("error reading from JSON; value {}", response.asUtf8String(), e);
        }
        return null;
    }

    record ClaudeModelRequest(String prompt, double temperature, double top_p, int top_k,
                              int max_tokens_to_sample, List<String> stop_sequences) {
    }

}

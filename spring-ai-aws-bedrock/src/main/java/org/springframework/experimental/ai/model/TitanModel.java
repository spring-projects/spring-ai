package org.springframework.experimental.ai.model;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;

public final class TitanModel extends AbstractAWSBaseModelParams {

    private static final Logger logger = LoggerFactory.getLogger(TitanModel.class);

    private final ObjectMapper mapper;

    @SuppressWarnings("unused")
    public TitanModel() {
        super("amazon.titan-text-agile-v1");
        mapper = new ObjectMapper();
    }

    public TitanModel(String modelId) {
        super(modelId);
        mapper = new ObjectMapper();
    }

    public TitanModel(String modelId, ObjectMapper mapper) {
        super(modelId);
        this.mapper = mapper;
    }

    @Override
    public SdkBytes toPayload(String prompt) {
        var textConfig = new TitanTextGenerationConfig(this.getTemperature(), this.getTopP(), this.getMaxToken(),
                Collections.emptyList());
        var params = new TitanParams(prompt, textConfig);
        try {
            return SdkBytes.fromByteArray(mapper.writeValueAsBytes(params));
        } catch (JsonProcessingException e) {
            logger.error("error serializing input to json", e);
            return null;
        }
    }

    @Override
    public String getResponseContent(SdkBytes response) {
        try {
            JsonNode json = mapper.readValue(response.asUtf8String(), JsonNode.class);
            return json.get("results").get(0).get("outputText").asText();
        } catch (JsonProcessingException e) {
            logger.error("error reading from JSON; value {}", response.asUtf8String(), e);
            return null;
        }
    }

}




package org.springframework.experimental.ai.model;

import software.amazon.awssdk.core.SdkBytes;

public interface AWSBaseModel {
    public SdkBytes toPayload(String prompt);

    public String getModelId();

    public String getResponseContent(SdkBytes response);

}

package org.springframework.experimental.ai.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.client.AiClient;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.prompt.Prompt;
import org.springframework.experimental.ai.model.AWSBaseModel;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;

import static java.util.Collections.singletonList;

public class AWSBedrockClient implements AiClient {

	private static final Logger logger = LoggerFactory.getLogger(AWSBedrockClient.class);

	private final BedrockRuntimeClient client;

	private final AWSBaseModel baseModelParams;

	public AWSBedrockClient(BedrockRuntimeClient client, AWSBaseModel baseModelParams) {
		this.client = client;
		this.baseModelParams = baseModelParams;
	}

	@Override
	public String generate(String message) {
		return invokeModel(message);
	}

	@Override
	public AiResponse generate(Prompt prompt) {
		var response = invokeModel(prompt.getContents());
		return new AiResponse(singletonList(new Generation(response)));
	}

	private String invokeModel(String msg) {

		var modelRequest = InvokeModelRequest.builder()
			.modelId(baseModelParams.getModelId())
			.contentType("application/json")
			.body(this.baseModelParams.toPayload(msg))
			.build();
		try {
			var response = client.invokeModel(modelRequest).body();
			return this.baseModelParams.getResponseContent(response);
		}
		catch (SdkException e) {
			logger.error("error invoking model for request {} with exception type {}", modelRequest, e.getClass(), e);
		}
		return null;
	}

}

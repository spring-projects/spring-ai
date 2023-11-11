package org.springframework.experimental.ai.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.experimental.ai.model.TitanModel;
import org.springframework.experimental.ai.model.TitanParams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

public class TitanInferenceTest {

	BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);

	TitanModel model = new TitanModel();

	AWSBedrockClient bedrockClient;

	ArgumentCaptor<InvokeModelRequest> requestCaptor = ArgumentCaptor.forClass(InvokeModelRequest.class);

	@BeforeEach
	void setup() {
		reset(client);
		model.setMaxToken(1);
		model.setTemperature(0.2);
		bedrockClient = new AWSBedrockClient(client, model);
	}

	@Test
	void testInferenceWorks() throws JsonProcessingException {
		var result = "Hello World";
		// payload according to
		// https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan.html#model-parameters-titan-response-body
		var responseStr = """
				{
				    "inputTextTokenCount": 20,
				    "results": [{
				        "tokenCount": 10,
				        "outputText": "%s",
				        "completionReason": "FINISHED"
				    }]
				}""".formatted(result);
		var response = InvokeModelResponse.builder()
			.body(SdkBytes.fromUtf8String(responseStr))
			.contentType("application/json")
			.build();
		when(client.invokeModel(requestCaptor.capture())).thenReturn(response);
		var inference = bedrockClient.generate("What is every first program in a new programming languages?");
		var request = requestCaptor.getValue();
		assertThat(request.contentType()).isEqualTo("application/json");
		var params = new ObjectMapper().readValue(request.body().asUtf8String(), TitanParams.class);
		assertThat(params.textGenerationConfig().maxTokenCount()).isEqualTo(1);
		assertThat(params.textGenerationConfig().temperature()).isEqualTo(0.2);
		assertThat(inference).isEqualTo(result);
	}

}

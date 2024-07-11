package org.springframework.ai.watsonx;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.watsonx.api.WatsonxAiApi;
import org.springframework.ai.watsonx.api.WatsonxAiEmbeddingRequest;
import org.springframework.ai.watsonx.api.WatsonxAiEmbeddingResponse;
import org.springframework.ai.watsonx.api.WatsonxAiEmbeddingResults;
import org.springframework.http.ResponseEntity;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WatsonxAiEmbeddingModelTest {

	private WatsonxAiApi watsonxAiApiMock;

	private final WatsonxAiEmbeddingModel embeddingModel;

	public WatsonxAiEmbeddingModelTest() {
		this.watsonxAiApiMock = mock(WatsonxAiApi.class);
		this.embeddingModel = new WatsonxAiEmbeddingModel(watsonxAiApiMock);
	}

	@Test
	void createRequestWithOptions() {
		String MODEL = "custom-model";
		List<String> inputs = List.of("test");
		WatsonxAiEmbeddingOptions options = WatsonxAiEmbeddingOptions.create().withModel(MODEL);

		WatsonxAiEmbeddingRequest request = embeddingModel.watsonxAiEmbeddingRequest(inputs, options);

		assertThat(request.getModel()).isEqualTo(MODEL);
		assertThat(request.getInputs().size()).isEqualTo(inputs.size());
	}

	@Test
	void createRequestWithOptionsAndInvalidModel() {
		String MODEL = "";
		List<String> inputs = List.of("test");
		WatsonxAiEmbeddingOptions options = WatsonxAiEmbeddingOptions.create().withModel(MODEL);

		WatsonxAiEmbeddingRequest request = embeddingModel.watsonxAiEmbeddingRequest(inputs, options);

		assertThat(request.getModel()).isEqualTo(WatsonxAiEmbeddingOptions.DEFAULT_MODEL);
		assertThat(request.getInputs().size()).isEqualTo(inputs.size());
	}

	@Test
	void createRequestWithNoOptions() {
		List<String> inputs = List.of("test");
		WatsonxAiEmbeddingRequest request = embeddingModel.watsonxAiEmbeddingRequest(inputs, EmbeddingOptions.EMPTY);

		assertThat(request.getModel()).isEqualTo(WatsonxAiEmbeddingOptions.DEFAULT_MODEL);
		assertThat(request.getInputs().size()).isEqualTo(inputs.size());
	}

	@Test
	void singleEmbeddingWithOptions() {
		List<String> inputs = List.of("test");

		String modelId = "mockId";
		Integer inputTokenCount = 2;
		float[] vector = new float[] { 1.0f, 2.0f };
		List<WatsonxAiEmbeddingResults> mockResults = List.of(new WatsonxAiEmbeddingResults(vector));
		WatsonxAiEmbeddingResponse mockResponse = new WatsonxAiEmbeddingResponse(modelId, new Date(), mockResults,
				inputTokenCount);

		ResponseEntity<WatsonxAiEmbeddingResponse> mockResponseEntity = ResponseEntity.ok(mockResponse);
		when(watsonxAiApiMock.embeddings(any(WatsonxAiEmbeddingRequest.class))).thenReturn(mockResponseEntity);

		assertThat(embeddingModel).isNotNull();

		EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingModel.dimensions()).isEqualTo(2);
	}

}

/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.vertexai.embedding.text;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictRequest;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingConnectionDetails;
import org.springframework.core.retry.RetryTemplate;

public class TestVertexAiTextEmbeddingModel extends VertexAiTextEmbeddingModel {

	private PredictionServiceClient mockPredictionServiceClient;

	private PredictRequest.Builder mockPredictRequestBuilder;

	public TestVertexAiTextEmbeddingModel(VertexAiEmbeddingConnectionDetails connectionDetails,
			VertexAiTextEmbeddingOptions defaultEmbeddingOptions, RetryTemplate retryTemplate) {
		super(connectionDetails, defaultEmbeddingOptions, retryTemplate);
	}

	public void setMockPredictionServiceClient(PredictionServiceClient mockPredictionServiceClient) {
		this.mockPredictionServiceClient = mockPredictionServiceClient;
	}

	@Override
	PredictionServiceClient createPredictionServiceClient() {
		if (this.mockPredictionServiceClient != null) {
			return this.mockPredictionServiceClient;
		}
		return super.createPredictionServiceClient();
	}

	@Override
	PredictResponse getPredictResponse(PredictionServiceClient client, PredictRequest.Builder predictRequestBuilder) {
		if (this.mockPredictionServiceClient != null) {
			return this.mockPredictionServiceClient.predict(predictRequestBuilder.build());
		}
		return super.getPredictResponse(client, predictRequestBuilder);
	}

	public void setMockPredictRequestBuilder(PredictRequest.Builder mockPredictRequestBuilder) {
		this.mockPredictRequestBuilder = mockPredictRequestBuilder;
	}

	@Override
	protected PredictRequest.Builder getPredictRequestBuilder(EmbeddingRequest request, EndpointName endpointName,
			VertexAiTextEmbeddingOptions finalOptions) {
		if (this.mockPredictRequestBuilder != null) {
			return this.mockPredictRequestBuilder;
		}
		return super.getPredictRequestBuilder(request, endpointName, finalOptions);
	}

}

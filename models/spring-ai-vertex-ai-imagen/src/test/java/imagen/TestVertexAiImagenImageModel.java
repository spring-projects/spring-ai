/*
 * Copyright 2025-2026 the original author or authors.
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
package imagen;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictRequest;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;

import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.vertexai.imagen.VertexAiImagenConnectionDetails;
import org.springframework.ai.vertexai.imagen.VertexAiImagenImageModel;
import org.springframework.ai.vertexai.imagen.VertexAiImagenImageOptions;
import org.springframework.retry.support.RetryTemplate;

/**
 * @author Sami Marzouki
 */
public class TestVertexAiImagenImageModel extends VertexAiImagenImageModel {

	private PredictionServiceClient mockPredictionServiceClient;

	private PredictRequest.Builder mockPredictRequestBuilder;

	public TestVertexAiImagenImageModel(VertexAiImagenConnectionDetails connectionDetails,
			VertexAiImagenImageOptions defaultOptions, RetryTemplate retryTemplate) {
		super(connectionDetails, defaultOptions, retryTemplate);
	}

	public void setMockPredictionServiceClient(PredictionServiceClient mockPredictionServiceClient) {
		this.mockPredictionServiceClient = mockPredictionServiceClient;
	}

	@Override
	public PredictionServiceClient createPredictionServiceClient() {
		if (this.mockPredictionServiceClient != null) {
			return this.mockPredictionServiceClient;
		}
		return super.createPredictionServiceClient();
	}

	@Override
	public PredictResponse getPredictResponse(PredictionServiceClient client,
			PredictRequest.Builder predictRequestBuilder) {
		if (this.mockPredictionServiceClient != null) {
			return this.mockPredictionServiceClient.predict(predictRequestBuilder.build());
		}
		return super.getPredictResponse(client, predictRequestBuilder);
	}

	public void setMockPredictRequestBuilder(PredictRequest.Builder mockPredictRequestBuilder) {
		this.mockPredictRequestBuilder = mockPredictRequestBuilder;
	}

	@Override
	protected PredictRequest.Builder getPredictRequestBuilder(ImagePrompt imagePrompt, EndpointName endpointName,
			VertexAiImagenImageOptions finalOptions) {
		if (this.mockPredictRequestBuilder != null) {
			return this.mockPredictRequestBuilder;
		}
		return super.getPredictRequestBuilder(imagePrompt, endpointName, finalOptions);
	}

}

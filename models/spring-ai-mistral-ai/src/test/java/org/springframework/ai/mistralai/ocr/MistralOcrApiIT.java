/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mistralai.ocr;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Mistral OCR API.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralOcrApiIT {

	MistralOcrApi mistralOcr = new MistralOcrApi(System.getenv("MISTRAL_AI_API_KEY"));

	@Test
	void ocrTest() {
		String documentUrl = "https://arxiv.org/pdf/2201.04234";
		MistralOcrApi.OCRRequest request = new MistralOcrApi.OCRRequest(
				MistralOcrApi.OCRModel.MISTRAL_OCR_LATEST.getValue(), "test_id",
				new MistralOcrApi.OCRRequest.DocumentURLChunk(documentUrl), List.of(0, 1, 2), true, 5, 50);

		ResponseEntity<MistralOcrApi.OCRResponse> response = this.mistralOcr.ocr(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().pages()).isNotNull();
		assertThat(response.getBody().pages()).isNotEmpty();
		assertThat(response.getBody().pages().get(0).markdown()).isNotEmpty();

		if (request.includeImageBase64() != null && request.includeImageBase64()) {
			assertThat(response.getBody().pages().get(1).images()).isNotNull();
			assertThat(response.getBody().pages().get(1).images().get(0).imageBase64()).isNotNull();
		}
	}

}

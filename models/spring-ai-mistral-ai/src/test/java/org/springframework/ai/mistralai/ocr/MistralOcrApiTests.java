/*
 * Copyright 2023-present the original author or authors.
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
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest.ResponseFormat;
import org.springframework.ai.mistralai.ocr.MistralOcrApi.OCRRequest;
import org.springframework.ai.mistralai.ocr.MistralOcrApi.OCRRequest.DocumentURLChunk;
import org.springframework.ai.mistralai.ocr.MistralOcrApi.OCRResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MistralOcrApiTests {

	private static final String BASE_URL = "https://mistral.test";

	private static final String MODEL = "mistral-ocr-latest";

	private static final DocumentURLChunk DOCUMENT = new DocumentURLChunk("https://example.com/invoice.pdf");

	private MockRestServiceServer server;

	private MistralOcrApi api;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.api = new MistralOcrApi(BASE_URL, "test-key", builder);
	}

	@Test
	void sendsDocumentAnnotationOptionsAndReadsFormattedResult() {
		ResponseFormat format = ResponseFormat.jsonSchema(Map.of("type", "object", "properties",
				Map.of("invoice_id", Map.of("type", "string")), "required", List.of("invoice_id"),
				"additionalProperties", false));
		OCRRequest request = new OCRRequest(MODEL, "test", DOCUMENT, List.of(0), false, 5, 50, format,
				"Extract the invoice ID");

		this.server.expect(requestTo(BASE_URL + "/v1/ocr"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.document.document_url").value(DOCUMENT.documentUrl()))
			.andExpect(jsonPath("$.document_annotation_format.type").value("json_schema"))
			.andExpect(jsonPath("$.document_annotation_format.json_schema.schema.properties.invoice_id.type")
				.value("string"))
			.andExpect(jsonPath("$.document_annotation_prompt").value("Extract the invoice ID"))
			.andRespond(withSuccess("""
					{
						"model": "mistral-ocr-latest",
						"pages": [{"index": 0, "markdown": "Invoice A-17", "images": []}],
						"usage_info": {"pages_processed": 1, "doc_size_bytes": 100},
						"document_annotation": "{\\"invoice_id\\":\\"A-17\\"}"
					}
					""", MediaType.APPLICATION_JSON));

		OCRResponse response = Objects.requireNonNull(this.api.ocr(request).getBody());
		assertThat(response.documentAnnotation()).isEqualTo("{\"invoice_id\":\"A-17\"}");
		assertThat(response.pages()).extracting(page -> page.markdown()).containsExactly("Invoice A-17");
		this.server.verify();
	}

	@Test
	void legacyRequestOmitsAnnotationOptions() {
		OCRRequest request = new OCRRequest(MODEL, "test", DOCUMENT, List.of(0), false, 5, 50);

		this.server.expect(requestTo(BASE_URL + "/v1/ocr"))
			.andExpect(jsonPath("$.model").value(MODEL))
			.andExpect(jsonPath("$.document_annotation_format").doesNotHaveJsonPath())
			.andExpect(jsonPath("$.document_annotation_prompt").doesNotHaveJsonPath())
			.andRespond(withSuccess("""
					{
						"model": "mistral-ocr-latest",
						"pages": [],
						"usage_info": {"pages_processed": 1, "doc_size_bytes": 100}
					}
					""", MediaType.APPLICATION_JSON));

		OCRResponse response = Objects.requireNonNull(this.api.ocr(request).getBody());
		assertThat(response.documentAnnotation()).isNull();
		this.server.verify();
	}

	@Test
	void rejectsDocumentAnnotationPromptWithoutFormat() {
		OCRRequest request = new OCRRequest(MODEL, "test", DOCUMENT, List.of(0), false, 5, 50, null,
				"Extract the invoice ID");

		assertThatIllegalArgumentException().isThrownBy(() -> this.api.ocr(request))
			.withMessageContaining("Document annotation format is required");
		this.server.verify();
	}

}

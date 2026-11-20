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

package org.springframework.ai.voyageai;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.DocumentEmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata.ModalityType;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

class VoyageAiMultimodalEmbeddingModelTests {

	private final MockVoyageAi voyage = new MockVoyageAi();

	private VoyageAiMultimodalEmbeddingModel model(String modelName) {
		return VoyageAiMultimodalEmbeddingModel.builder()
			.voyageAiApi(this.voyage.api)
			.options(VoyageAiMultimodalEmbeddingOptions.builder().model(modelName).build())
			.retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
			.build();
	}

	private static DocumentEmbeddingRequest request(Document... documents) {
		return new DocumentEmbeddingRequest(List.of(documents), EmbeddingOptions.builder().build());
	}

	private static Document image(Object data, MimeType mimeType) {
		Media.Builder media = Media.builder().mimeType(mimeType);
		Media built = (data instanceof byte[] bytes) ? media.data(bytes).build() : media.data((String) data).build();
		return new Document(built, java.util.Map.of());
	}

	@Test
	void sendsTextAndMediaAsContentPartsAndReportsTheirModality() {
		this.voyage.expectPost("/v1/multimodalembeddings")
			.andExpect(jsonPath("$.inputs[0].content[0].type").value("text"))
			.andExpect(jsonPath("$.inputs[0].content[0].text").value("a banana"))
			.andExpect(jsonPath("$.inputs[1].content[0].type").value("image_base64"))
			.andExpect(jsonPath("$.inputs[1].content[0].image_base64").value("data:image/png;base64,AQID"))
			.andExpect(jsonPath("$.inputs[1].content.length()").value(1))
			.andRespond(MockVoyageAi.embeddings(2, 0, ",\"usage\":{\"total_tokens\":4,\"text_tokens\":2}"));

		EmbeddingResponse response = model("voyage-multimodal-3.5")
			.call(request(new Document("a banana"), image(new byte[] { 1, 2, 3 }, MimeTypeUtils.IMAGE_PNG)));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(0).getMetadata().getModalityType()).isEqualTo(ModalityType.TEXT);
		assertThat(response.getResults().get(1).getMetadata().getModalityType()).isEqualTo(ModalityType.IMAGE);
		assertThat(response.getResults().get(1).getOutput()).containsExactly(1.0f);
		this.voyage.server.verify();
	}

	@Test
	void sendsAnImageUrlAsAUrl() {
		this.voyage.expectPost("/v1/multimodalembeddings")
			.andExpect(jsonPath("$.inputs[0].content[0].type").value("image_url"))
			.andExpect(jsonPath("$.inputs[0].content[0].image_url").value("https://example.com/a.jpg"))
			.andRespond(MockVoyageAi.embeddings(1, 0, MockVoyageAi.USAGE));
		model("voyage-multimodal-3")
			.call(request(new Document(new Media(MimeTypeUtils.IMAGE_JPEG, URI.create("https://example.com/a.jpg")),
					java.util.Map.of())));
		this.voyage.server.verify();
	}

	@Test
	void embedsTextAndMediaTogetherAsOneInput() {
		this.voyage.expectPost("/v1/multimodalembeddings")
			.andExpect(jsonPath("$.inputs.length()").value(1))
			.andExpect(jsonPath("$.inputs[0].content.length()").value(3))
			.andExpect(jsonPath("$.inputs[0].content[0].text").value("a banana"))
			.andExpect(jsonPath("$.inputs[0].content[1].type").value("image_base64"))
			.andExpect(jsonPath("$.inputs[0].content[2].text").value("on a table"))
			.andRespond(MockVoyageAi.embeddings(1, 0, MockVoyageAi.USAGE));

		var input = VoyageAiMultimodalInput.builder()
			.id("banana")
			.text("a banana")
			.media(Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(new byte[] { 1, 2, 3 }).build())
			.text("on a table")
			.build();
		EmbeddingResponse response = model("voyage-multimodal-3.5").embed(List.of(input), null);

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getMetadata().getDocumentId()).isEqualTo("banana");
		assertThat(response.getResults().get(0).getMetadata().getModalityType()).isEqualTo(ModalityType.IMAGE);
		this.voyage.server.verify();
	}

	@Test
	void anInputNeedsAtLeastOnePartAndNoBlankText() {
		assertThatIllegalArgumentException().isThrownBy(() -> VoyageAiMultimodalInput.builder().build())
			.withMessageContaining("at least one part");
		assertThatIllegalArgumentException().isThrownBy(() -> VoyageAiMultimodalInput.builder().text(" ").build());
	}

	@Test
	void rejectsARequestThatMixesUrlsAndBase64() {
		var model = model("voyage-multimodal-3.5");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> model
				.call(request(new Document(new Media(MimeTypeUtils.IMAGE_PNG, URI.create("https://example.com/a.png")),
						java.util.Map.of()), image(new byte[] { 1 }, MimeTypeUtils.IMAGE_PNG))))
			.withMessageContaining("either all as URLs or all as base64");
		this.voyage.server.verify();
	}

	@Test
	void rejectsUnsupportedMediaBeforeSendingAnything() {
		var model = model("voyage-multimodal-3.5");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> model.call(request(image(new byte[] { 1 }, MimeType.valueOf("image/bmp")))))
			.withMessageContaining("Unsupported image type");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> model.call(request(image(new byte[] { 1 }, MimeType.valueOf("application/pdf")))))
			.withMessageContaining("expected an image or a video");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> model.call(request(image(new byte[] { 1 }, MimeType.valueOf("video/webm")))))
			.withMessageContaining("Unsupported video type");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> model.call(request(image("file:///etc/passwd", MimeTypeUtils.IMAGE_PNG))))
			.withMessageContaining("bytes of a file, an http(s) URL or a base64 data URL");
		var legacy = model("voyage-multimodal-3");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> legacy.call(request(image(new byte[] { 1 }, MimeType.valueOf("video/mp4")))))
			.withMessageContaining("does not support video");
		this.voyage.server.verify();
	}

	@Test
	void failsWithoutAModelOrWithAModelOfAnotherEndpoint() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> VoyageAiMultimodalEmbeddingModel.builder()
				.voyageAiApi(this.voyage.api)
				.build()
				.call(request(new Document("text"))))
			.withMessageContaining("no default model");
		assertThatIllegalArgumentException().isThrownBy(() -> model("voyage-4").call(request(new Document("text"))))
			.withMessageContaining("is not a Voyage AI multimodal embedding model");
	}

	@Test
	void failsClosedWhenTheResponseHasNoUsage() {
		this.voyage.expectPost("/v1/multimodalembeddings").andRespond(MockVoyageAi.embeddings(1, 0, ""));
		var model = model("voyage-multimodal-3.5");
		org.assertj.core.api.Assertions.assertThatIllegalStateException()
			.isThrownBy(() -> model.call(request(new Document("text"))))
			.withMessageContaining("no usage.total_tokens");
	}

	@Test
	void requestOptionsCanChooseInputTypeAndDimension() {
		this.voyage.expectPost("/v1/multimodalembeddings")
			.andExpect(jsonPath("$.input_type").value("query"))
			.andExpect(jsonPath("$.output_dimension").value(512))
			.andRespond(MockVoyageAi.embeddings(1, 0, MockVoyageAi.USAGE));
		model("voyage-multimodal-3.5").call(new DocumentEmbeddingRequest(List.of(new Document("text")),
				VoyageAiMultimodalEmbeddingOptions.builder()
					.inputType(VoyageAiApi.InputType.QUERY)
					.dimensions(512)
					.build()));
		this.voyage.server.verify();
	}

}

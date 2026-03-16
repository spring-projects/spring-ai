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

package org.springframework.ai.vertexai.gemini;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.CitationMetadata;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GroundingChunk;
import com.google.cloud.vertexai.api.GroundingSupport;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.api.SearchEntryPoint;
import com.google.cloud.vertexai.api.Segment;
import com.google.type.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.vertexai.gemini.common.Citation;
import org.springframework.ai.vertexai.gemini.common.GroundingMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for citation and grounding metadata extraction in
 * {@link VertexAiGeminiChatModel#responseCandidateToGeneration(Candidate)}.
 *
 * @author Arun Subbaiah
 */
@ExtendWith(MockitoExtension.class)
class CitationTests {

	@Mock
	private VertexAI vertexAI;

	private VertexAiGeminiChatModel chatModel;

	@BeforeEach
	void setUp() {
		this.chatModel = new VertexAiGeminiChatModel(this.vertexAI,
				VertexAiGeminiChatOptions.builder()
					.temperature(0.7)
					.topP(1.0)
					.model(VertexAiGeminiChatModel.ChatModel.GEMINI_2_0_FLASH.getValue())
					.build(),
				ToolCallingManager.builder().build(),
				org.springframework.ai.retry.RetryUtils.DEFAULT_RETRY_TEMPLATE, null);
	}

	@Test
	void responseCandidateWithCitationMetadata() {
		Candidate candidate = Candidate.newBuilder()
			.setContent(Content.newBuilder()
				.addParts(Part.newBuilder().setText("The Eiffel Tower is in Paris.").build())
				.build())
			.setCitationMetadata(CitationMetadata.newBuilder()
				.addCitations(com.google.cloud.vertexai.api.Citation.newBuilder()
					.setStartIndex(0)
					.setEndIndex(28)
					.setUri("https://example.com/eiffel")
					.setTitle("Eiffel Tower Facts")
					.setLicense("CC-BY-4.0")
					.setPublicationDate(Date.newBuilder().setYear(2024).setMonth(3).setDay(15).build())
					.build())
				.addCitations(com.google.cloud.vertexai.api.Citation.newBuilder()
					.setStartIndex(10)
					.setEndIndex(28)
					.setUri("https://example.com/paris")
					.setTitle("Paris Guide")
					.setLicense("")
					.build())
				.build())
			.build();

		List<Generation> generations = this.chatModel.responseCandidateToGeneration(candidate);

		assertThat(generations).hasSize(1);
		Map<String, Object> metadata = generations.get(0).getOutput().getMetadata();

		assertThat(metadata).containsKey("citations");
		@SuppressWarnings("unchecked")
		List<Citation> citations = (List<Citation>) metadata.get("citations");

		assertThat(citations).hasSize(2);

		Citation first = citations.get(0);
		assertThat(first.startIndex()).isEqualTo(0);
		assertThat(first.endIndex()).isEqualTo(28);
		assertThat(first.uri()).isEqualTo("https://example.com/eiffel");
		assertThat(first.title()).isEqualTo("Eiffel Tower Facts");
		assertThat(first.license()).isEqualTo("CC-BY-4.0");
		assertThat(first.publicationDate()).isEqualTo(LocalDate.of(2024, 3, 15));

		Citation second = citations.get(1);
		assertThat(second.startIndex()).isEqualTo(10);
		assertThat(second.endIndex()).isEqualTo(28);
		assertThat(second.uri()).isEqualTo("https://example.com/paris");
		assertThat(second.title()).isEqualTo("Paris Guide");
		assertThat(second.publicationDate()).isNull();
	}

	@Test
	void responseCandidateWithGroundingMetadata() {
		Candidate candidate = Candidate.newBuilder()
			.setContent(Content.newBuilder()
				.addParts(Part.newBuilder().setText("Blackbeard was a famous pirate.").build())
				.build())
			.setGroundingMetadata(com.google.cloud.vertexai.api.GroundingMetadata.newBuilder()
				.addWebSearchQueries("famous pirates history")
				.addWebSearchQueries("Blackbeard pirate")
				.setSearchEntryPoint(
						SearchEntryPoint.newBuilder().setRenderedContent("<div>Search results</div>").build())
				.addGroundingChunks(GroundingChunk.newBuilder()
					.setWeb(GroundingChunk.Web.newBuilder()
						.setUri("https://example.com/pirates")
						.setTitle("Famous Pirates")
						.build())
					.build())
				.addGroundingChunks(GroundingChunk.newBuilder()
					.setRetrievedContext(GroundingChunk.RetrievedContext.newBuilder()
						.setUri("https://datastore.example.com/doc1")
						.setTitle("Pirate History")
						.setText("Blackbeard was Edward Teach...")
						.build())
					.build())
				.addGroundingSupports(GroundingSupport.newBuilder()
					.setSegment(Segment.newBuilder()
						.setPartIndex(0)
						.setStartIndex(0)
						.setEndIndex(30)
						.setText("Blackbeard was a famous pirate.")
						.build())
					.addGroundingChunkIndices(0)
					.addGroundingChunkIndices(1)
					.addConfidenceScores(0.95f)
					.addConfidenceScores(0.87f)
					.build())
				.build())
			.build();

		List<Generation> generations = this.chatModel.responseCandidateToGeneration(candidate);

		assertThat(generations).hasSize(1);
		Map<String, Object> metadata = generations.get(0).getOutput().getMetadata();

		assertThat(metadata).containsKey("groundingMetadata");
		GroundingMetadata grounding = (GroundingMetadata) metadata
			.get("groundingMetadata");

		// Web search queries
		assertThat(grounding.webSearchQueries()).containsExactly("famous pirates history", "Blackbeard pirate");

		// Search entry point
		assertThat(grounding.searchEntryPoint()).isNotNull();
		assertThat(grounding.searchEntryPoint().renderedContent()).isEqualTo("<div>Search results</div>");

		// Grounding chunks
		assertThat(grounding.groundingChunks()).hasSize(2);

		GroundingMetadata.GroundingChunk webChunk = grounding.groundingChunks().get(0);
		assertThat(webChunk.web()).isNotNull();
		assertThat(webChunk.web().uri()).isEqualTo("https://example.com/pirates");
		assertThat(webChunk.web().title()).isEqualTo("Famous Pirates");
		assertThat(webChunk.retrievedContext()).isNull();

		GroundingMetadata.GroundingChunk retrievedChunk = grounding.groundingChunks().get(1);
		assertThat(retrievedChunk.web()).isNull();
		assertThat(retrievedChunk.retrievedContext()).isNotNull();
		assertThat(retrievedChunk.retrievedContext().uri()).isEqualTo("https://datastore.example.com/doc1");
		assertThat(retrievedChunk.retrievedContext().title()).isEqualTo("Pirate History");
		assertThat(retrievedChunk.retrievedContext().text()).isEqualTo("Blackbeard was Edward Teach...");

		// Grounding supports
		assertThat(grounding.groundingSupports()).hasSize(1);

		GroundingMetadata.GroundingSupport support = grounding.groundingSupports().get(0);
		assertThat(support.segment()).isNotNull();
		assertThat(support.segment().partIndex()).isEqualTo(0);
		assertThat(support.segment().startIndex()).isEqualTo(0);
		assertThat(support.segment().endIndex()).isEqualTo(30);
		assertThat(support.segment().text()).isEqualTo("Blackbeard was a famous pirate.");
		assertThat(support.groundingChunkIndices()).containsExactly(0, 1);
		assertThat(support.confidenceScores()).containsExactly(0.95f, 0.87f);
	}

	@Test
	void responseCandidateWithBothCitationAndGroundingMetadata() {
		Candidate candidate = Candidate.newBuilder()
			.setContent(Content.newBuilder()
				.addParts(Part.newBuilder().setText("Some grounded text.").build())
				.build())
			.setCitationMetadata(CitationMetadata.newBuilder()
				.addCitations(com.google.cloud.vertexai.api.Citation.newBuilder()
					.setStartIndex(0)
					.setEndIndex(19)
					.setUri("https://example.com/source")
					.setTitle("Source")
					.build())
				.build())
			.setGroundingMetadata(com.google.cloud.vertexai.api.GroundingMetadata.newBuilder()
				.addWebSearchQueries("test query")
				.addGroundingChunks(GroundingChunk.newBuilder()
					.setWeb(GroundingChunk.Web.newBuilder()
						.setUri("https://example.com/web")
						.setTitle("Web Result")
						.build())
					.build())
				.build())
			.build();

		List<Generation> generations = this.chatModel.responseCandidateToGeneration(candidate);

		assertThat(generations).hasSize(1);
		Map<String, Object> metadata = generations.get(0).getOutput().getMetadata();

		assertThat(metadata).containsKey("citations");
		assertThat(metadata).containsKey("groundingMetadata");

		@SuppressWarnings("unchecked")
		List<Citation> citations = (List<Citation>) metadata.get("citations");
		assertThat(citations).hasSize(1);

		GroundingMetadata grounding = (GroundingMetadata) metadata
			.get("groundingMetadata");
		assertThat(grounding.webSearchQueries()).containsExactly("test query");
		assertThat(grounding.groundingChunks()).hasSize(1);
	}

	@Test
	void responseCandidateWithoutCitationOrGroundingMetadata() {
		Candidate candidate = Candidate.newBuilder()
			.setContent(Content.newBuilder()
				.addParts(Part.newBuilder().setText("Plain response without citations.").build())
				.build())
			.build();

		List<Generation> generations = this.chatModel.responseCandidateToGeneration(candidate);

		assertThat(generations).hasSize(1);
		Map<String, Object> metadata = generations.get(0).getOutput().getMetadata();

		assertThat(metadata).doesNotContainKey("citations");
		assertThat(metadata).doesNotContainKey("groundingMetadata");

		// Existing metadata keys should still be present
		assertThat(metadata).containsKey("candidateIndex");
		assertThat(metadata).containsKey("finishReason");
		assertThat(metadata).containsKey("logprobs");
		assertThat(metadata).containsKey("safetyRatings");
	}

	@Test
	void responseCandidateWithEmptyCitationMetadata() {
		Candidate candidate = Candidate.newBuilder()
			.setContent(Content.newBuilder()
				.addParts(Part.newBuilder().setText("Response text.").build())
				.build())
			.setCitationMetadata(CitationMetadata.newBuilder().build())
			.build();

		List<Generation> generations = this.chatModel.responseCandidateToGeneration(candidate);

		assertThat(generations).hasSize(1);
		Map<String, Object> metadata = generations.get(0).getOutput().getMetadata();

		// Empty citation list should not be added to metadata
		assertThat(metadata).doesNotContainKey("citations");
	}

	@Test
	void responseCandidateWithGroundingMetadataWithoutSearchEntryPoint() {
		Candidate candidate = Candidate.newBuilder()
			.setContent(Content.newBuilder()
				.addParts(Part.newBuilder().setText("Response text.").build())
				.build())
			.setGroundingMetadata(com.google.cloud.vertexai.api.GroundingMetadata.newBuilder()
				.addWebSearchQueries("test")
				.addGroundingChunks(GroundingChunk.newBuilder()
					.setWeb(GroundingChunk.Web.newBuilder().setUri("https://example.com").setTitle("Example").build())
					.build())
				.build())
			.build();

		List<Generation> generations = this.chatModel.responseCandidateToGeneration(candidate);

		assertThat(generations).hasSize(1);
		GroundingMetadata grounding = (GroundingMetadata) generations.get(0)
			.getOutput()
			.getMetadata()
			.get("groundingMetadata");

		assertThat(grounding).isNotNull();
		assertThat(grounding.searchEntryPoint()).isNull();
		assertThat(grounding.webSearchQueries()).containsExactly("test");
	}

}

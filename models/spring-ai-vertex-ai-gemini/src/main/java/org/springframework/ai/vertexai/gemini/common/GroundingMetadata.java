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

package org.springframework.ai.vertexai.gemini.common;

import java.util.List;

import org.springframework.lang.Nullable;

/**
 * Represents grounding metadata returned by the Vertex AI Gemini API when Google Search
 * grounding is enabled. Contains web search queries used, the sources found, and mappings
 * between response segments and their supporting sources with confidence scores.
 *
 * @param webSearchQueries the web search queries used by the model for grounding
 * @param searchEntryPoint the search entry point for displaying search results, or null
 * @param groundingChunks the source chunks (web pages or retrieved contexts) used for
 * grounding
 * @param groundingSupports the mappings between response text segments and their
 * supporting grounding chunks
 * @author Arun Subbaiah
 * @since 1.0.0
 */
public record GroundingMetadata(List<String> webSearchQueries,
                                @Nullable SearchEntryPoint searchEntryPoint, List<GroundingChunk> groundingChunks,
                                List<GroundingSupport> groundingSupports) {

	/**
	 * Represents a search entry point containing rendered HTML content for displaying
	 * search results.
	 *
	 * @param renderedContent the rendered HTML content for the search entry point
	 */
	public record SearchEntryPoint(String renderedContent) {

	}

	/**
	 * Represents a single grounding source chunk, which can be either a web page or a
	 * retrieved context from a data store.
	 *
	 * @param web the web source, or null if this is a retrieved context
	 * @param retrievedContext the retrieved context, or null if this is a web source
	 */
	public record GroundingChunk(@Nullable WebSource web, @Nullable RetrievedContext retrievedContext) {

		/**
		 * Represents a web source used for grounding.
		 *
		 * @param uri the URI of the web page
		 * @param title the title of the web page
		 */
		public record WebSource(String uri, String title) {
		}

		/**
		 * Represents a retrieved context from a data store used for grounding.
		 *
		 * @param uri the URI of the retrieved context
		 * @param title the title of the retrieved context
		 * @param text the text content of the retrieved context
		 */
		public record RetrievedContext(String uri, String title, String text) {
		}

	}

	/**
	 * Represents a mapping between a segment of the response text and the grounding
	 * chunks that support it, along with confidence scores.
	 *
	 * @param segment the segment of response text that is grounded
	 * @param groundingChunkIndices the indices into the groundingChunks list identifying
	 * supporting sources
	 * @param confidenceScores the confidence scores for each supporting source
	 */
	public record GroundingSupport(Segment segment, List<Integer> groundingChunkIndices,
			List<Float> confidenceScores) {

		/**
		 * Represents a segment of the response text.
		 *
		 * @param partIndex the index of the part in the response content
		 * @param startIndex the start character index of the segment (inclusive)
		 * @param endIndex the end character index of the segment (exclusive)
		 * @param text the text content of the segment
		 */
		public record Segment(int partIndex, int startIndex, int endIndex, String text) {

		}

	}

}

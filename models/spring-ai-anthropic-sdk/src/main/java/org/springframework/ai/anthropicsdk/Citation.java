/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.anthropicsdk;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Represents a citation reference in a Claude response. Citations indicate which parts of
 * the provided documents were referenced when generating the response.
 *
 * <p>
 * Citations are returned in the response metadata under the "citations" key and include:
 * <ul>
 * <li>The cited text from the document</li>
 * <li>The document index (which document was cited)</li>
 * <li>The document title (if provided)</li>
 * <li>Location information (character ranges, page numbers, or content block
 * indices)</li>
 * </ul>
 *
 * <h3>Citation Types</h3>
 * <ul>
 * <li><b>CHAR_LOCATION:</b> For plain text documents, includes character start/end
 * indices</li>
 * <li><b>PAGE_LOCATION:</b> For PDF documents, includes page start/end numbers</li>
 * <li><b>CONTENT_BLOCK_LOCATION:</b> For custom content documents, includes block
 * start/end indices</li>
 * </ul>
 *
 * @author Soby Chacko
 * @since 2.0.0
 * @see AnthropicSdkCitationDocument
 */
public final class Citation {

	/**
	 * Types of citation locations based on document format.
	 */
	public enum LocationType {

		/** Character-based location for plain text documents */
		CHAR_LOCATION,

		/** Page-based location for PDF documents */
		PAGE_LOCATION,

		/** Block-based location for custom content documents */
		CONTENT_BLOCK_LOCATION

	}

	private final LocationType type;

	private final String citedText;

	private final int documentIndex;

	private final @Nullable String documentTitle;

	// Location-specific fields
	private @Nullable Integer startCharIndex;

	private @Nullable Integer endCharIndex;

	private @Nullable Integer startPageNumber;

	private @Nullable Integer endPageNumber;

	private @Nullable Integer startBlockIndex;

	private @Nullable Integer endBlockIndex;

	// Private constructor
	private Citation(LocationType type, String citedText, int documentIndex, @Nullable String documentTitle) {
		this.type = type;
		this.citedText = citedText;
		this.documentIndex = documentIndex;
		this.documentTitle = documentTitle;
	}

	/**
	 * Create a character location citation for plain text documents.
	 * @param citedText the text that was cited from the document
	 * @param documentIndex the index of the document (0-based)
	 * @param documentTitle the title of the document
	 * @param startCharIndex the starting character index (0-based, inclusive)
	 * @param endCharIndex the ending character index (exclusive)
	 * @return a new Citation with CHAR_LOCATION type
	 */
	public static Citation ofCharLocation(String citedText, int documentIndex, @Nullable String documentTitle,
			int startCharIndex, int endCharIndex) {
		Citation citation = new Citation(LocationType.CHAR_LOCATION, citedText, documentIndex, documentTitle);
		citation.startCharIndex = startCharIndex;
		citation.endCharIndex = endCharIndex;
		return citation;
	}

	/**
	 * Create a page location citation for PDF documents.
	 * @param citedText the text that was cited from the document
	 * @param documentIndex the index of the document (0-based)
	 * @param documentTitle the title of the document
	 * @param startPageNumber the starting page number (1-based, inclusive)
	 * @param endPageNumber the ending page number (exclusive)
	 * @return a new Citation with PAGE_LOCATION type
	 */
	public static Citation ofPageLocation(String citedText, int documentIndex, @Nullable String documentTitle,
			int startPageNumber, int endPageNumber) {
		Citation citation = new Citation(LocationType.PAGE_LOCATION, citedText, documentIndex, documentTitle);
		citation.startPageNumber = startPageNumber;
		citation.endPageNumber = endPageNumber;
		return citation;
	}

	/**
	 * Create a content block location citation for custom content documents.
	 * @param citedText the text that was cited from the document
	 * @param documentIndex the index of the document (0-based)
	 * @param documentTitle the title of the document
	 * @param startBlockIndex the starting content block index (0-based, inclusive)
	 * @param endBlockIndex the ending content block index (exclusive)
	 * @return a new Citation with CONTENT_BLOCK_LOCATION type
	 */
	public static Citation ofContentBlockLocation(String citedText, int documentIndex, @Nullable String documentTitle,
			int startBlockIndex, int endBlockIndex) {
		Citation citation = new Citation(LocationType.CONTENT_BLOCK_LOCATION, citedText, documentIndex, documentTitle);
		citation.startBlockIndex = startBlockIndex;
		citation.endBlockIndex = endBlockIndex;
		return citation;
	}

	public LocationType getType() {
		return this.type;
	}

	public String getCitedText() {
		return this.citedText;
	}

	public int getDocumentIndex() {
		return this.documentIndex;
	}

	public @Nullable String getDocumentTitle() {
		return this.documentTitle;
	}

	public @Nullable Integer getStartCharIndex() {
		return this.startCharIndex;
	}

	public @Nullable Integer getEndCharIndex() {
		return this.endCharIndex;
	}

	public @Nullable Integer getStartPageNumber() {
		return this.startPageNumber;
	}

	public @Nullable Integer getEndPageNumber() {
		return this.endPageNumber;
	}

	public @Nullable Integer getStartBlockIndex() {
		return this.startBlockIndex;
	}

	public @Nullable Integer getEndBlockIndex() {
		return this.endBlockIndex;
	}

	/**
	 * Get a human-readable location description.
	 */
	public String getLocationDescription() {
		return switch (this.type) {
			case CHAR_LOCATION -> String.format("Characters %d-%d", this.startCharIndex, this.endCharIndex);
			case PAGE_LOCATION -> {
				Assert.state(this.startPageNumber != null, "startPageNumber must be defined with page-based location");
				Assert.state(this.endPageNumber != null, "endPageNumber must be defined with page-based location");
				yield this.startPageNumber.equals(this.endPageNumber - 1)
						? String.format("Page %d", this.startPageNumber)
						: String.format("Pages %d-%d", this.startPageNumber, this.endPageNumber - 1);
			}
			case CONTENT_BLOCK_LOCATION -> {
				Assert.state(this.startBlockIndex != null, "startBlockIndex must be defined with block-based location");
				Assert.state(this.endBlockIndex != null, "endBlockIndex must be defined with block-based location");
				yield this.startBlockIndex.equals(this.endBlockIndex - 1)
						? String.format("Block %d", this.startBlockIndex)
						: String.format("Blocks %d-%d", this.startBlockIndex, this.endBlockIndex - 1);
			}
		};
	}

	@Override
	public String toString() {
		return String.format("Citation{type=%s, documentIndex=%d, documentTitle='%s', location='%s', citedText='%s'}",
				this.type, this.documentIndex, this.documentTitle, getLocationDescription(),
				this.citedText.length() > 50 ? this.citedText.substring(0, 50) + "..." : this.citedText);
	}

}

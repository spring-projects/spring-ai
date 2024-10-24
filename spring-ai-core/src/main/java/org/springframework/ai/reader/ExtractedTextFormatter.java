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

package org.springframework.ai.reader;

import org.springframework.util.StringUtils;

/**
 * A utility to reformat extracted text content before encapsulating it in a
 * {@link org.springframework.ai.document.Document}. This formatter provides the following
 * functionalities:
 *
 * <ul>
 * <li>Left alignment of text</li>
 * <li>Removal of specified lines from the beginning and end of content</li>
 * <li>Consolidation of consecutive blank lines</li>
 * </ul>
 *
 * An instance of this formatter can be customized using the {@link Builder} nested class.
 *
 * @author Christian Tzolov
 */
public final class ExtractedTextFormatter {

	/** Flag indicating if the text should be left-aligned */
	private final boolean leftAlignment;

	/** Number of top pages to skip before performing delete operations */
	private final int numberOfTopPagesToSkipBeforeDelete;

	/** Number of top text lines to delete from a page */
	private final int numberOfTopTextLinesToDelete;

	/** Number of bottom text lines to delete from a page */
	private final int numberOfBottomTextLinesToDelete;

	/**
	 * Private constructor to initialize the formatter from the builder.
	 * @param builder Builder used to initialize the formatter.
	 */
	private ExtractedTextFormatter(Builder builder) {
		this.leftAlignment = builder.leftAlignment;
		this.numberOfBottomTextLinesToDelete = builder.numberOfBottomTextLinesToDelete;
		this.numberOfTopPagesToSkipBeforeDelete = builder.numberOfTopPagesToSkipBeforeDelete;
		this.numberOfTopTextLinesToDelete = builder.numberOfTopTextLinesToDelete;
	}

	/**
	 * Provides an instance of the builder for this formatter.
	 * @return an instance of the builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Provides a default instance of the formatter.
	 * @return default instance of the formatter.
	 */
	public static ExtractedTextFormatter defaults() {
		return new Builder().build();
	}

	/**
	 * Replaces multiple, adjacent blank lines into a single blank line.
	 * @param pageText text to adjust the blank lines for.
	 * @return Returns the same text but with blank lines trimmed.
	 */
	public static String trimAdjacentBlankLines(String pageText) {
		return pageText.replaceAll("(?m)(^ *\n)", "\n").replaceAll("(?m)^$([\r\n]+?)(^$[\r\n]+?^)+", "$1");
	}

	/**
	 * @param pageText text to align.
	 * @return Returns the same text but aligned to the left side.
	 */
	public static String alignToLeft(String pageText) {
		return pageText.replaceAll("(?m)(^ *| +(?= |$))", "").replaceAll("(?m)^$(	?)(^$[\r\n]+?^)+", "$1");
	}

	/**
	 * Removes the specified number of lines from the bottom part of the text.
	 * @param pageText Text to remove lines from.
	 * @param numberOfLines Number of lines to remove.
	 * @return Returns the text striped from last lines.
	 */
	public static String deleteBottomTextLines(String pageText, int numberOfLines) {
		if (!StringUtils.hasText(pageText)) {
			return pageText;
		}

		int lineCount = 0;
		int truncateIndex = pageText.length();
		int nextTruncateIndex = truncateIndex;
		while (lineCount < numberOfLines && nextTruncateIndex >= 0) {
			nextTruncateIndex = pageText.lastIndexOf(System.lineSeparator(), truncateIndex - 1);
			truncateIndex = nextTruncateIndex < 0 ? truncateIndex : nextTruncateIndex;
			lineCount++;
		}
		return pageText.substring(0, truncateIndex);
	}

	/**
	 * Removes a specified number of lines from the top part of the given text.
	 *
	 * <p>
	 * This method takes a text and trims it by removing a certain number of lines from
	 * the top. If the provided text is null or contains only whitespace, it will be
	 * returned as is. If the number of lines to remove exceeds the actual number of lines
	 * in the text, the result will be an empty string.
	 * </p>
	 *
	 * <p>
	 * The method identifies lines based on the system's line separator, making it
	 * compatible with different platforms.
	 * </p>
	 * @param pageText The text from which the top lines need to be removed. If this is
	 * null, empty, or consists only of whitespace, it will be returned unchanged.
	 * @param numberOfLines The number of lines to remove from the top of the text. If
	 * this exceeds the actual number of lines in the text, an empty string will be
	 * returned.
	 * @return The text with the specified number of lines removed from the top.
	 */
	public static String deleteTopTextLines(String pageText, int numberOfLines) {
		if (!StringUtils.hasText(pageText)) {
			return pageText;
		}
		int lineCount = 0;

		int truncateIndex = 0;
		int nextTruncateIndex = truncateIndex;
		while (lineCount < numberOfLines && nextTruncateIndex >= 0) {
			nextTruncateIndex = pageText.indexOf(System.lineSeparator(), truncateIndex + 1);
			truncateIndex = nextTruncateIndex < 0 ? truncateIndex : nextTruncateIndex;
			lineCount++;
		}
		return pageText.substring(truncateIndex);
	}

	/**
	 * Formats the provided text according to the formatter's configuration.
	 * @param pageText Text to be formatted.
	 * @return Formatted text.
	 */
	public String format(String pageText) {
		return this.format(pageText, 0);
	}

	/**
	 * Formats the provided text based on the formatter's configuration, considering the
	 * page number.
	 * @param pageText Text to be formatted.
	 * @param pageNumber Page number of the provided text.
	 * @return Formatted text.
	 */
	public String format(String pageText, int pageNumber) {

		var text = trimAdjacentBlankLines(pageText);

		if (pageNumber >= this.numberOfTopPagesToSkipBeforeDelete) {
			text = deleteTopTextLines(text, this.numberOfTopTextLinesToDelete);
			text = deleteBottomTextLines(text, this.numberOfBottomTextLinesToDelete);
		}

		if (this.leftAlignment) {
			text = alignToLeft(text);
		}

		return text;
	}

	/**
	 * The {@code Builder} class is a nested static class of
	 * {@link ExtractedTextFormatter} designed to facilitate the creation and
	 * customization of instances of {@link ExtractedTextFormatter}.
	 *
	 * <p>
	 * It allows for a step-by-step, fluent construction of the
	 * {@link ExtractedTextFormatter}, by providing methods to set specific configurations
	 * such as left alignment of text, the number of top lines or bottom lines to delete,
	 * and the number of top pages to skip before deletion. Each configuration method in
	 * the builder returns the builder instance itself, enabling method chaining.
	 * </p>
	 *
	 *
	 * By default, the builder sets:
	 * <ul>
	 * <li>Left alignment to {@code false}</li>
	 * <li>Number of top pages to skip before deletion to 0</li>
	 * <li>Number of top text lines to delete to 0</li>
	 * <li>Number of bottom text lines to delete to 0</li>
	 * </ul>
	 *
	 *
	 * <p>
	 * After configuring the builder, calling the {@link #build()} method will return a
	 * new instance of {@link ExtractedTextFormatter} with the specified configurations.
	 * </p>
	 *
	 * @see ExtractedTextFormatter
	 */
	public static class Builder {

		private boolean leftAlignment = false;

		private int numberOfTopPagesToSkipBeforeDelete = 0;

		private int numberOfTopTextLinesToDelete = 0;

		private int numberOfBottomTextLinesToDelete = 0;

		/**
		 * Align the document text to the left. Defaults to false.
		 * @param leftAlignment Flag to align the text to the left.
		 * @return this builder
		 */
		public Builder withLeftAlignment(boolean leftAlignment) {
			this.leftAlignment = leftAlignment;
			return this;
		}

		/**
		 * Withdraw the top N pages from the text top/bottom line deletion. Defaults to 0.
		 * @param numberOfTopPagesToSkipBeforeDelete Number of pages to skip from
		 * top/bottom line deletion policy.
		 * @return this builder
		 */
		public Builder withNumberOfTopPagesToSkipBeforeDelete(int numberOfTopPagesToSkipBeforeDelete) {
			this.numberOfTopPagesToSkipBeforeDelete = numberOfTopPagesToSkipBeforeDelete;
			return this;
		}

		/**
		 * Remove the top N lines from the page text. Defaults to 0.
		 * @param numberOfTopTextLinesToDelete Number of top text lines to delete.
		 * @return this builder
		 */
		public Builder withNumberOfTopTextLinesToDelete(int numberOfTopTextLinesToDelete) {
			this.numberOfTopTextLinesToDelete = numberOfTopTextLinesToDelete;
			return this;
		}

		/**
		 * Remove the bottom N lines from the page text. Defaults to 0.
		 * @param numberOfBottomTextLinesToDelete Number of bottom text lines to delete.
		 * @return this builder
		 */
		public Builder withNumberOfBottomTextLinesToDelete(int numberOfBottomTextLinesToDelete) {
			this.numberOfBottomTextLinesToDelete = numberOfBottomTextLinesToDelete;
			return this;
		}

		/**
		 * Constructs and returns an instance of {@link ExtractedTextFormatter} using the
		 * configurations set on this builder.
		 *
		 * <p>
		 * This method uses the values set on the builder to initialize the configuration
		 * for the {@link ExtractedTextFormatter} instance. If no values are explicitly
		 * set on the builder, the defaults specified in the builder are used.
		 * </p>
		 *
		 * <p>
		 * It's recommended to use this method only once per builder instance to ensure
		 * that each {@link ExtractedTextFormatter} object is configured as intended.
		 * </p>
		 * @return a new instance of {@link ExtractedTextFormatter} configured with the
		 * values set on this builder.
		 */
		public ExtractedTextFormatter build() {
			return new ExtractedTextFormatter(this);
		}

	}

}

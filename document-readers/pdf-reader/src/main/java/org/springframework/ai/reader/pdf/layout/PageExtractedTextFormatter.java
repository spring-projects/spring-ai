/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.reader.pdf.layout;

import org.springframework.util.StringUtils;

/**
 * Provides text formatting options for extracted PDF page text, including left alignment
 * and the ability to trim and delete lines from the top and bottom of the text.
 *
 * This class allows customization of text formatting applied to extracted PDF page text.
 * It can align the text to the left, remove specified lines from the top and bottom of
 * the text, and trim adjacent blank lines.
 *
 * @author Christian Tzolov
 */
public class PageExtractedTextFormatter {

	private boolean leftAlignment;

	private int numberOfTopPagesToSkipBeforeDelete;

	private int numberOfTopTextLinesToDelete;

	private int numberOfBottomTextLinesToDelete;

	private PageExtractedTextFormatter(Builder builder) {
		this.leftAlignment = builder.leftAlignment;
		this.numberOfBottomTextLinesToDelete = builder.numberOfBottomTextLinesToDelete;
		this.numberOfTopPagesToSkipBeforeDelete = builder.numberOfTopPagesToSkipBeforeDelete;
		this.numberOfTopTextLinesToDelete = builder.numberOfTopTextLinesToDelete;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static PageExtractedTextFormatter defaults() {
		return new Builder().build();
	}

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

		public PageExtractedTextFormatter build() {
			return new PageExtractedTextFormatter(this);
		}

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
		return pageText.substring(truncateIndex, pageText.length());
	}

}

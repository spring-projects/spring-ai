/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.reader.pdf;

import org.junit.jupiter.api.Test;

import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 */
public class ParagraphPdfDocumentReaderTests {

	@Test
	public void testPdfWithoutToc() {

		assertThatThrownBy(() -> {

			new ParagraphPdfDocumentReader("classpath:/sample1.pdf",
					PdfDocumentReaderConfig.builder()
						.withPageTopMargin(0)
						.withPageBottomMargin(0)
						.withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
							.withNumberOfTopTextLinesToDelete(0)
							.withNumberOfBottomTextLinesToDelete(3)
							.withNumberOfTopPagesToSkipBeforeDelete(0)
							.build())
						.withPagesPerDocument(1)
						.build());
		}).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Document outline (e.g. TOC) is null. Make sure the PDF document has a table of contents (TOC). If not, consider the PagePdfDocumentReader or the TikaDocumentReader instead.");

	}

}

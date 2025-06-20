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

package org.springframework.ai.reader.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author Christian Tzolov
 * @author Heonwoo Kim
 */
public class ParagraphPdfDocumentReaderTests {

	@Test
	public void testPdfWithoutToc() {

		assertThatThrownBy(() ->

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
					.build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Document outline (e.g. TOC) is null. Make sure the PDF document has a table of contents (TOC). If not, consider the PagePdfDocumentReader or the TikaDocumentReader instead.");

	}

	@Test
	void shouldSkipInvalidOutline() throws IOException {

		Resource basePdfResource = new ClassPathResource("sample3.pdf");

		PDDocument documentToModify;
		try (InputStream inputStream = basePdfResource.getInputStream()) {

			byte[] pdfBytes = inputStream.readAllBytes();

			documentToModify = Loader.loadPDF(pdfBytes);
		}
		PDDocumentOutline outline = documentToModify.getDocumentCatalog().getDocumentOutline();
		if (outline != null && outline.getFirstChild() != null) {
			PDOutlineItem chapter2OutlineItem = outline.getFirstChild().getNextSibling();
			if (chapter2OutlineItem != null) {

				chapter2OutlineItem.setDestination((PDDestination) null);
			}
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		documentToModify.save(baos);
		documentToModify.close();

		Resource corruptedPdfResource = new ByteArrayResource(baos.toByteArray());

		ParagraphPdfDocumentReader reader = new ParagraphPdfDocumentReader(corruptedPdfResource,
				PdfDocumentReaderConfig.defaultConfig());

		List<Document> documents = assertDoesNotThrow(() -> reader.get());

		assertThat(documents).isNotNull();
		assertThat(documents).hasSize(2);
		assertThat(documents.get(0).getMetadata().get("title")).isEqualTo("Chapter 1");
		assertThat(documents.get(1).getMetadata().get("title")).isEqualTo("Chapter 3");
	}

}

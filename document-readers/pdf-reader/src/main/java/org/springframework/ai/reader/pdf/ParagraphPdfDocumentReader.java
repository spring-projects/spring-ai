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

package org.springframework.ai.reader.pdf;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.config.ParagraphManager;
import org.springframework.ai.reader.pdf.config.ParagraphManager.Paragraph;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.pdf.layout.PDFLayoutTextStripperByArea;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Uses the PDF catalog (e.g. TOC) information to split the input PDF into text paragraphs
 * and output a single {@link Document} per paragraph.
 *
 * This class provides methods for reading and processing PDF documents. It uses the
 * Apache PDFBox library for parsing PDF content and converting it into text paragraphs.
 * The paragraphs are grouped into {@link Document} objects.
 *
 * @author Christian Tzolov
 */
public class ParagraphPdfDocumentReader implements DocumentReader {

	// Constants for metadata keys
	private static final String METADATA_START_PAGE = "page_number";

	private static final String METADATA_END_PAGE = "end_page_number";

	private static final String METADATA_TITLE = "title";

	private static final String METADATA_LEVEL = "level";

	private static final String METADATA_FILE_NAME = "file_name";

	protected final PDDocument document;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ParagraphManager paragraphTextExtractor;

	protected String resourceFileName;

	private PdfDocumentReaderConfig config;

	/**
	 * Constructs a ParagraphPdfDocumentReader using a resource URL.
	 * @param resourceUrl The URL of the PDF resource.
	 */
	public ParagraphPdfDocumentReader(String resourceUrl) {
		this(new DefaultResourceLoader().getResource(resourceUrl));
	}

	/**
	 * Constructs a ParagraphPdfDocumentReader using a resource.
	 * @param pdfResource The PDF resource.
	 */
	public ParagraphPdfDocumentReader(Resource pdfResource) {
		this(pdfResource, PdfDocumentReaderConfig.defaultConfig());
	}

	/**
	 * Constructs a ParagraphPdfDocumentReader using a resource URL and a configuration.
	 * @param resourceUrl The URL of the PDF resource.
	 * @param config The configuration for PDF document processing.
	 */
	public ParagraphPdfDocumentReader(String resourceUrl, PdfDocumentReaderConfig config) {
		this(new DefaultResourceLoader().getResource(resourceUrl), config);
	}

	/**
	 * Constructs a ParagraphPdfDocumentReader using a resource and a configuration.
	 * @param pdfResource The PDF resource.
	 * @param config The configuration for PDF document processing.
	 */
	public ParagraphPdfDocumentReader(Resource pdfResource, PdfDocumentReaderConfig config) {

		try {
			PDFParser pdfParser = new PDFParser(
					new org.apache.pdfbox.io.RandomAccessReadBuffer(pdfResource.getInputStream()));
			this.document = pdfParser.parse();

			this.config = config;

			this.paragraphTextExtractor = new ParagraphManager(this.document);

			this.resourceFileName = pdfResource.getFilename();
		}
		catch (IllegalArgumentException iae) {
			throw iae;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Reads and processes the PDF document to extract paragraphs.
	 * @return A list of {@link Document} objects representing paragraphs.
	 */
	@Override
	public List<Document> get() {

		var paragraphs = this.paragraphTextExtractor.flatten();

		List<Document> documents = new ArrayList<>(paragraphs.size());

		if (!CollectionUtils.isEmpty(paragraphs)) {
			logger.info("Start processing paragraphs from PDF");
			Iterator<Paragraph> itr = paragraphs.iterator();

			var current = itr.next();

			if (!itr.hasNext()) {
				documents.add(toDocument(current, current));
			}
			else {
				while (itr.hasNext()) {
					var next = itr.next();
					Document document = toDocument(current, next);
					if (document != null && StringUtils.hasText(document.getText())) {
						documents.add(toDocument(current, next));
					}
					current = next;
				}
			}
		}
		logger.info("End processing paragraphs from PDF");
		return documents;
	}

	protected Document toDocument(Paragraph from, Paragraph to) {

		String docText = this.getTextBetweenParagraphs(from, to);

		if (!StringUtils.hasText(docText)) {
			return null;
		}

		Document document = new Document(docText);
		addMetadata(from, to, document);

		return document;
	}

	protected void addMetadata(Paragraph from, Paragraph to, Document document) {
		document.getMetadata().put(METADATA_TITLE, from.title());
		document.getMetadata().put(METADATA_START_PAGE, from.startPageNumber());
		document.getMetadata().put(METADATA_END_PAGE, to.startPageNumber());
		document.getMetadata().put(METADATA_LEVEL, from.level());
		document.getMetadata().put(METADATA_FILE_NAME, this.resourceFileName);
	}

	public String getTextBetweenParagraphs(Paragraph fromParagraph, Paragraph toParagraph) {

		// Page started from index 0, while PDFBOx getPage return them from index 1.
		int startPage = fromParagraph.startPageNumber() - 1;
		int endPage = toParagraph.startPageNumber() - 1;

		try {

			StringBuilder sb = new StringBuilder();

			var pdfTextStripper = new PDFLayoutTextStripperByArea();
			pdfTextStripper.setSortByPosition(true);

			for (int pageNumber = startPage; pageNumber <= endPage; pageNumber++) {

				var page = this.document.getPage(pageNumber);

				int fromPosition = fromParagraph.position();
				int toPosition = toParagraph.position();

				if (this.config.reversedParagraphPosition) {
					fromPosition = (int) (page.getMediaBox().getHeight() - fromPosition);
					toPosition = (int) (page.getMediaBox().getHeight() - toPosition);
				}

				int x0 = (int) page.getMediaBox().getLowerLeftX();
				int xW = (int) page.getMediaBox().getWidth();

				int y0 = (int) page.getMediaBox().getLowerLeftY();
				int yW = (int) page.getMediaBox().getHeight();

				if (pageNumber == startPage) {
					y0 = fromPosition;
					yW = (int) page.getMediaBox().getHeight() - y0;
				}
				if (pageNumber == endPage) {
					yW = toPosition - y0;
				}

				if ((y0 + yW) == (int) page.getMediaBox().getHeight()) {
					yW = yW - this.config.pageBottomMargin;
				}

				if (y0 == 0) {
					y0 = y0 + this.config.pageTopMargin;
					yW = yW - this.config.pageTopMargin;
				}

				pdfTextStripper.addRegion("pdfPageRegion", new Rectangle(x0, y0, xW, yW));
				pdfTextStripper.extractRegions(page);
				var text = pdfTextStripper.getTextForRegion("pdfPageRegion");
				if (StringUtils.hasText(text)) {
					sb.append(text);
				}
				pdfTextStripper.removeRegion("pdfPageRegion");

			}

			String text = sb.toString();

			if (StringUtils.hasText(text)) {
				text = this.config.pageExtractedTextFormatter.format(text, startPage);
			}

			return text;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}

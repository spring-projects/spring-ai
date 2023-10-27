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

package org.springframework.ai.reader.pdf;

import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.pdf.layout.PDFLayoutTextStripperByArea;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Groups the parsed PDF pages into {@link Document}s. You can group one or more pages
 * into a single output document. Use {@link PdfDocumentReaderConfig} for customization
 * options. The default configuration is: - pagesPerDocument = 1 - pageTopMargin = 0 -
 * pageBottomMargin = 0
 *
 * @author Christian Tzolov
 * @author Craig Walls
 */
public class PagePdfDocumentReader implements DocumentReader {

	private static final String PDF_PAGE_REGION = "pdfPageRegion";

	public static final String METADATA_START_PAGE_NUMBER = "page_number";

	public static final String METADATA_END_PAGE_NUMBER = "end_page_number";

	public static final String METADATA_FILE_NAME = "file_name";

	private PdfDocumentReaderConfig config;

	/**
	 * Constructs a ParagraphPdfDocumentReader with default configuration.
	 */
	public PagePdfDocumentReader() {
		this(PdfDocumentReaderConfig.defaultConfig());
	}

	/**
	 * Constructs a PagePdfDocumentReader using a specific configuration.
	 * @param config The configuration for PDF document processing.
	 */
	public PagePdfDocumentReader(PdfDocumentReaderConfig config) {
		this.config = config;
	}

	/**
	 * Read and process the PDF document from the given resource URL.
	 * @param resourceUrl The URL of the PDF document to read.
	 */
	@Override
	public List<Document> read(String resourceUrl) {
		return read(new DefaultResourceLoader().getResource(resourceUrl));
	}

	/**
	 * Read and process the PDF document from the given resource.
	 * @param resource The PDF document to read.
	 */
	@Override
	public List<Document> read(Resource resource) {

		try {
			File resourceFileName = resource.getFile();

			PDFParser pdfParser = new PDFParser(
					new org.apache.pdfbox.io.RandomAccessReadBuffer(resource.getInputStream()));
			PDDocument document = pdfParser.parse();

			List<Document> readDocuments = new ArrayList<>();
			var pdfTextStripper = new PDFLayoutTextStripperByArea();

			int pageNumber = 0;
			int pagesPerDocument = 0;
			int startPageNumber = pageNumber;

			List<String> pageTextGroupList = new ArrayList<>();

			for (PDPage page : document.getDocumentCatalog().getPages()) {

				pagesPerDocument++;

				if (this.config.pagesPerDocument != PdfDocumentReaderConfig.ALL_PAGES
						&& pagesPerDocument >= this.config.pagesPerDocument) {
					pagesPerDocument = 0;

					var aggregatedPageTextGroup = pageTextGroupList.stream().collect(Collectors.joining());
					if (StringUtils.hasText(aggregatedPageTextGroup)) {
						readDocuments
							.add(toDocument(resourceFileName, aggregatedPageTextGroup, startPageNumber, pageNumber));
					}
					pageTextGroupList.clear();

					startPageNumber = pageNumber + 1;
				}
				int x0 = (int) page.getMediaBox().getLowerLeftX();
				int xW = (int) page.getMediaBox().getWidth();

				int y0 = (int) page.getMediaBox().getLowerLeftY() + this.config.pageTopMargin;
				int yW = (int) page.getMediaBox().getHeight()
						- (this.config.pageTopMargin + this.config.pageBottomMargin);

				pdfTextStripper.addRegion(PDF_PAGE_REGION, new Rectangle(x0, y0, xW, yW));
				pdfTextStripper.extractRegions(page);
				var pageText = pdfTextStripper.getTextForRegion(PDF_PAGE_REGION);

				if (StringUtils.hasText(pageText)) {

					pageText = this.config.pageExtractedTextFormatter.format(pageText, pageNumber);

					pageTextGroupList.add(pageText);
				}
				pageNumber++;
				pdfTextStripper.removeRegion(PDF_PAGE_REGION);
			}
			if (!CollectionUtils.isEmpty(pageTextGroupList)) {
				readDocuments.add(toDocument(resourceFileName, pageTextGroupList.stream().collect(Collectors.joining()),
						startPageNumber, pageNumber));
			}

			return readDocuments;

		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Document toDocument(File resourceFileName, String docText, int startPageNumber, int endPageNumber) {

		Document doc = new Document(docText);
		doc.getMetadata().put(METADATA_START_PAGE_NUMBER, startPageNumber);
		if (startPageNumber != endPageNumber) {
			doc.getMetadata().put(METADATA_END_PAGE_NUMBER, endPageNumber);
		}
		doc.getMetadata().put(METADATA_FILE_NAME, resourceFileName);

		return doc;
	}

}

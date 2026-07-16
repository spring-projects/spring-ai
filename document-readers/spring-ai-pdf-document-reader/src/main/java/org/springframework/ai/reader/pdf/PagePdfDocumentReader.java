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

package org.springframework.ai.reader.pdf;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.jspecify.annotations.Nullable;

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
 * @author Fu Jian
 */
public class PagePdfDocumentReader implements DocumentReader {

	public static final String METADATA_START_PAGE_NUMBER = "page_number";

	public static final String METADATA_END_PAGE_NUMBER = "end_page_number";

	public static final String METADATA_FILE_NAME = "file_name";

	private static final String PDF_PAGE_REGION = "pdfPageRegion";

	protected final PDDocument document;

	private final Log logger = LogFactory.getLog(getClass());

	protected @Nullable String resourceFileName;

	private final PdfDocumentReaderConfig config;

	public PagePdfDocumentReader(String resourceUrl) {
		this(new DefaultResourceLoader().getResource(resourceUrl));
	}

	public PagePdfDocumentReader(Resource pdfResource) {
		this(pdfResource, PdfDocumentReaderConfig.defaultConfig());
	}

	public PagePdfDocumentReader(String resourceUrl, PdfDocumentReaderConfig config) {
		this(new DefaultResourceLoader().getResource(resourceUrl), config);
	}

	public PagePdfDocumentReader(Resource pdfResource, PdfDocumentReaderConfig config) {
		try {
			PDFParser pdfParser = new PDFParser(
					new org.apache.pdfbox.io.RandomAccessReadBuffer(pdfResource.getInputStream()));
			this.document = pdfParser.parse();

			this.resourceFileName = pdfResource.getFilename();
			this.config = config;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<Document> get() {

		List<Document> readDocuments = new ArrayList<>();
		try {
			var pdfTextStripper = new PDFLayoutTextStripperByArea();

			List<String> pageTextGroupList = new ArrayList<>();

			PDPageTree pages = this.document.getDocumentCatalog().getPages();
			int totalPages = pages.getCount();
			int logFrequency = totalPages > 10 ? totalPages / 10 : 1;

			// When all pages are grouped into a single Document, never flush mid-stream;
			// the trailing flush below emits the single Document.
			int pagesPerDocument = this.config.pagesPerDocument == PdfDocumentReaderConfig.ALL_PAGES ? Integer.MAX_VALUE
					: this.config.pagesPerDocument;

			int pageNumber = 0;
			int selectedPageCount = 0;
			int processedInGroup = 0;
			int groupStartPage = 0;
			int groupEndPage = 0;
			int previousSelectedPage = 0;
			for (PDPage page : pages) {
				pageNumber++;
				// Skip pages outside the configured page ranges (if any) while keeping
				// the
				// sequential page-tree iteration and the physical page numbering intact.
				if (!this.config.isPageSelected(pageNumber)) {
					continue;
				}

				// With disjoint page ranges, flush the current group before a gap so a
				// Document never spans excluded pages and its start/end page metadata
				// stays accurate. As a result pagesPerDocument acts as a maximum across
				// gaps rather than merging non-contiguous pages.
				if (processedInGroup > 0 && pageNumber != previousSelectedPage + 1) {
					flushGroup(readDocuments, pageTextGroupList, groupStartPage, groupEndPage);
					groupStartPage = 0;
					processedInGroup = 0;
				}

				if (selectedPageCount % logFrequency == 0) {
					if (logger.isInfoEnabled()) {
						logger.info("Processing PDF page: " + pageNumber);
					}
				}

				if (groupStartPage == 0) {
					groupStartPage = pageNumber;
				}
				handleSinglePage(page, pageNumber, pdfTextStripper, pageTextGroupList);
				groupEndPage = pageNumber;
				processedInGroup++;
				selectedPageCount++;
				previousSelectedPage = pageNumber;

				if (processedInGroup == pagesPerDocument) {
					flushGroup(readDocuments, pageTextGroupList, groupStartPage, groupEndPage);
					groupStartPage = 0;
					processedInGroup = 0;
				}
			}

			// Emit any pages left in a partially filled group (also the single Document
			// produced when all selected pages are grouped together).
			flushGroup(readDocuments, pageTextGroupList, groupStartPage, groupEndPage);

			if (logger.isInfoEnabled()) {
				logger.info("Processed " + selectedPageCount + " selected pages of " + totalPages + " total");
			}
			return readDocuments;
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void handleSinglePage(PDPage page, int pageNumber, PDFLayoutTextStripperByArea pdfTextStripper,
			List<String> pageTextGroupList) throws IOException {
		int x0 = (int) page.getMediaBox().getLowerLeftX();
		int xW = (int) page.getMediaBox().getWidth();

		int y0 = (int) page.getMediaBox().getLowerLeftY() + this.config.pageTopMargin;
		int yW = (int) page.getMediaBox().getHeight() - (this.config.pageTopMargin + this.config.pageBottomMargin);

		pdfTextStripper.addRegion(PDF_PAGE_REGION, new Rectangle(x0, y0, xW, yW));
		pdfTextStripper.extractRegions(page);
		var pageText = pdfTextStripper.getTextForRegion(PDF_PAGE_REGION);

		if (StringUtils.hasText(pageText)) {
			pageText = this.config.pageExtractedTextFormatter.format(pageText, pageNumber);
			pageTextGroupList.add(pageText);
		}
		pdfTextStripper.removeRegion(PDF_PAGE_REGION);
	}

	private void flushGroup(List<Document> readDocuments, List<String> pageTextGroupList, int startPageNumber,
			int endPageNumber) {
		if (!CollectionUtils.isEmpty(pageTextGroupList)) {
			readDocuments.add(toDocument(String.join("", pageTextGroupList), startPageNumber, endPageNumber));
			pageTextGroupList.clear();
		}
	}

	protected Document toDocument(String docText, int startPageNumber, int endPageNumber) {
		Document doc = new Document(docText);
		doc.getMetadata().put(METADATA_START_PAGE_NUMBER, startPageNumber);
		if (startPageNumber != endPageNumber) {
			doc.getMetadata().put(METADATA_END_PAGE_NUMBER, endPageNumber);
		}
		if (this.resourceFileName != null) {
			doc.getMetadata().put(METADATA_FILE_NAME, this.resourceFileName);
		}
		return doc;
	}

}

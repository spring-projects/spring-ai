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

package org.springframework.ai.reader.tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

/**
 * A document reader that leverages Apache Tika to extract text from a variety of document
 * formats, such as PDF, DOC/DOCX, PPT/PPTX, and HTML. For a comprehensive list of
 * supported formats, refer to: https://tika.apache.org/3.1.0/formats.html.
 *
 * This reader directly provides the extracted text without any additional formatting. All
 * extracted texts are encapsulated within a {@link Document} instance.
 *
 * If you require more specialized handling for PDFs, consider using the
 * PagePdfDocumentReader or ParagraphPdfDocumentReader.
 *
 * @author Christian Tzolov
 */

public class TikaDocumentReader implements DocumentReader {

	/**
	 * Metadata key representing the source of the document.
	 */
	public static final String METADATA_SOURCE = "source";

	/**
	 * Parser to automatically detect the type of document and extract text.
	 */
	private final AutoDetectParser parser;

	/**
	 * Supplier of handlers to manage content extraction. Invoked on every {@link #get()} call
	 * so that each extraction parses into a fresh {@link ContentHandler}.
	 */
	private final Supplier<ContentHandler> contentHandlerSupplier;

	/**
	 * Parsing context containing information about the parsing process.
	 */
	private final ParseContext context;

	/**
	 * The resource pointing to the document.
	 */
	private final Resource resource;

	/**
	 * Formatter for the extracted text.
	 */
	private final ExtractedTextFormatter textFormatter;

	/**
	 * Constructor initializing the reader with a given resource URL.
	 * @param resourceUrl URL to the resource
	 */
	public TikaDocumentReader(String resourceUrl) {
		this(resourceUrl, ExtractedTextFormatter.defaults());
	}

	/**
	 * Constructor initializing the reader with a given resource URL and a text formatter.
	 * @param resourceUrl URL to the resource
	 * @param textFormatter Formatter for the extracted text
	 */
	public TikaDocumentReader(String resourceUrl, ExtractedTextFormatter textFormatter) {
		this(new DefaultResourceLoader().getResource(resourceUrl), textFormatter);
	}

	/**
	 * Constructor initializing the reader with a resource.
	 * @param resource Resource pointing to the document
	 */
	public TikaDocumentReader(Resource resource) {
		this(resource, ExtractedTextFormatter.defaults());
	}

	/**
	 * Constructor initializing the reader with a resource and a text formatter. This
	 * constructor will create a BodyContentHandler that allows for reading large PDFs
	 * (constrained only by memory)
	 * @param resource Resource pointing to the document
	 * @param textFormatter Formatter for the extracted text
	 */
	public TikaDocumentReader(Resource resource, ExtractedTextFormatter textFormatter) {
		this(resource, () -> new BodyContentHandler(-1), textFormatter);
	}

	/**
	 * Constructor initializing the reader with a resource, content handler supplier, and a text
	 * formatter. The supplier is invoked on every call to {@link #get()}, so each extraction
	 * parses into a fresh {@link ContentHandler}, making the reader safe to call repeatedly.
	 * @param resource Resource pointing to the document
	 * @param contentHandlerSupplier Supplier of handler to manage content extraction
	 * @param textFormatter Formatter for the extracted text
	 */
	public TikaDocumentReader(Resource resource, Supplier<ContentHandler> contentHandlerSupplier,
			ExtractedTextFormatter textFormatter) {
		this.parser = new AutoDetectParser();
		this.contentHandlerSupplier = contentHandlerSupplier;
		this.context = new ParseContext();
		this.resource = resource;
		this.textFormatter = textFormatter;
	}

	/**
	 * Constructor initializing the reader with a resource, content handler, and a text formatter.
	 * @param resource Resource pointing to the document
	 * @param contentHandler Handler to manage content extraction
	 * @param textFormatter Formatter for the extracted text
	 * @deprecated the supplied handler instance is reused for every extraction and accumulates
	 * text across calls to {@link #get()}; use
	 * {@link #TikaDocumentReader(Resource, Supplier, ExtractedTextFormatter)} instead
	 */
	@Deprecated
	public TikaDocumentReader(Resource resource, ContentHandler contentHandler, ExtractedTextFormatter textFormatter) {
		this(resource, () -> contentHandler, textFormatter);
	}

	/**
	 * Extracts and returns the list of documents from the resource.
	 * @return List of extracted {@link Document}
	 */
	@Override
	public List<Document> get() {
		try (InputStream stream = this.resource.getInputStream()) {
			ContentHandler contentHandler = this.contentHandlerSupplier.get();
			Metadata parseMetadata = new Metadata();
			this.parser.parse(stream, contentHandler, parseMetadata, this.context);
			return List.of(toDocument(contentHandler.toString()));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Converts the given text to a {@link Document}.
	 * @param docText Text to be converted
	 * @return Converted document
	 */
	private Document toDocument(String docText) {
		docText = Objects.requireNonNullElse(docText, "");
		docText = this.textFormatter.format(docText);
		Document doc = new Document(docText);
		doc.getMetadata().put(METADATA_SOURCE, resourceName());
		return doc;
	}

	/**
	 * Returns the name of the resource. If the filename is not present, it returns the
	 * URI of the resource.
	 * @return Name or URI of the resource
	 */
	private String resourceName() {
		try {
			var resourceName = this.resource.getFilename();
			if (!StringUtils.hasText(resourceName)) {
				resourceName = this.resource.getURI().toString();
			}
			return resourceName;
		}
		catch (IOException e) {
			return String.format("Invalid source URI: %s", e.getMessage());
		}
	}

}

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

import org.apache.tika.exception.WriteLimitReachedException;
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
import org.springframework.util.Assert;
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
 * The extracted text is held in memory, so by default this reader extracts at most
 * {@link #DEFAULT_MAX_CHARACTERS} characters per document and fails if a document exceeds
 * that limit. Use {@link #TikaDocumentReader(Resource, int, ExtractedTextFormatter)} to
 * configure a different limit when reading documents from trusted sources.
 *
 * @author Christian Tzolov
 * @author ikaitist
 * @author Dimitar Proynov
 */

public class TikaDocumentReader implements DocumentReader {

	/**
	 * Metadata key representing the source of the document.
	 */
	public static final String METADATA_SOURCE = "source";

	/**
	 * The default maximum number of characters extracted from a single document. Prevents
	 * a large or highly expanding document from exhausting the JVM heap. Roughly
	 * equivalent to a few thousand pages of text, which is far beyond the size of typical
	 * documents.
	 */
	public static final int DEFAULT_MAX_CHARACTERS = 10_000_000;

	/**
	 * Parser to automatically detect the type of document and extract text.
	 */
	private final AutoDetectParser parser;

	/**
	 * Supplies the handler that manages content extraction for a single {@link #get()}
	 * call.
	 */
	private final Supplier<ContentHandler> contentHandlerSupplier;

	/**
	 * The maximum number of characters to extract, or {@code -1} if unlimited.
	 */
	private final int maxCharacters;

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
	 * Constructor initializing the reader with a resource and a text formatter. Extracts
	 * at most {@link #DEFAULT_MAX_CHARACTERS} characters.
	 * @param resource Resource pointing to the document
	 * @param textFormatter Formatter for the extracted text
	 */
	public TikaDocumentReader(Resource resource, ExtractedTextFormatter textFormatter) {
		this(resource, DEFAULT_MAX_CHARACTERS, textFormatter);
	}

	/**
	 * Constructor initializing the reader with a resource, a maximum number of characters
	 * to extract, and a text formatter.
	 * @param resource Resource pointing to the document
	 * @param maxCharacters Maximum number of characters to extract, or {@code -1} to
	 * disable the limit. Because the extracted text is held in memory, disabling the
	 * limit is only safe for documents from trusted sources.
	 * @param textFormatter Formatter for the extracted text
	 * @throws IllegalArgumentException if {@code maxCharacters} is lower than {@code -1}
	 * @since 2.0.2.1
	 */
	public TikaDocumentReader(Resource resource, int maxCharacters, ExtractedTextFormatter textFormatter) {
		this(resource, () -> new BodyContentHandler(maxCharacters), maxCharacters, textFormatter);
	}

	/**
	 * Constructor initializing the reader with a resource, content handler, and a text
	 * formatter. The given handler is reused for every call to {@link #get()}, so it
	 * collects the text of each call on top of the previous ones. Any limit on the
	 * extracted text is the responsibility of the handler.
	 * @param resource Resource pointing to the document
	 * @param contentHandler Handler to manage content extraction
	 * @param textFormatter Formatter for the extracted text
	 */
	public TikaDocumentReader(Resource resource, ContentHandler contentHandler, ExtractedTextFormatter textFormatter) {
		this(resource, () -> contentHandler, -1, textFormatter);
	}

	private TikaDocumentReader(Resource resource, Supplier<ContentHandler> contentHandlerSupplier, int maxCharacters,
			ExtractedTextFormatter textFormatter) {
		Assert.isTrue(maxCharacters >= -1, "maxCharacters must be -1 or greater");
		this.parser = new AutoDetectParser();
		this.contentHandlerSupplier = contentHandlerSupplier;
		this.maxCharacters = maxCharacters;
		this.resource = resource;
		this.textFormatter = textFormatter;
	}

	/**
	 * Extracts and returns the list of documents from the resource.
	 * @return List of extracted {@link Document}
	 * @throws IllegalStateException if the extracted text exceeds the configured maximum
	 * number of characters
	 */
	@Override
	public List<Document> get() {
		ContentHandler contentHandler = this.contentHandlerSupplier.get();
		try (InputStream stream = this.resource.getInputStream()) {
			this.parser.parse(stream, contentHandler, new Metadata(), new ParseContext());
			return List.of(toDocument(contentHandler.toString()));
		}
		catch (Exception e) {
			if (WriteLimitReachedException.isWriteLimitReached(e)) {
				throw new IllegalStateException(writeLimitReachedMessage(), e);
			}
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns the message reported when the extracted text exceeds the write limit.
	 * @return Message describing the exceeded limit
	 */
	private String writeLimitReachedMessage() {
		if (this.maxCharacters < 0) {
			return "Text extracted from '%s' exceeds the content handler write limit".formatted(resourceName());
		}
		return "Text extracted from '%s' exceeds the maximum of %d characters".formatted(resourceName(),
				this.maxCharacters);
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

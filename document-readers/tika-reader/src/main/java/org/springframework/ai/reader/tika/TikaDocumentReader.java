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

package org.springframework.ai.reader.tika;

import java.io.IOException;
import java.util.List;

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
 * Tika based document reader, that extracts text from various document formats, including
 * pdf, doc/docx, ppt/pptx, html. Fof the full list of supported documents check the
 * https://tika.apache.org/2.9.0/formats.html.
 *
 * The reader does not provide any additional pre/post formatting for the extracted text.
 * The extracted text is wrapped in a {@link Document}.
 *
 * For an advanced PDF document reader consult the PagePdfDocumentReader and
 * ParagraphPdfDocumentReader instead.
 *
 * @author Christian Tzolov
 */
public class TikaDocumentReader implements DocumentReader {

	public static final String METADATA_SOURCE = "source";

	private final AutoDetectParser parser;

	private final ContentHandler handler;

	private final Metadata metadata;

	private final ParseContext context;

	private final Resource resource;

	private final ExtractedTextFormatter textFormatter;

	public TikaDocumentReader(String resourceUrl) {
		this(resourceUrl, ExtractedTextFormatter.defaults());
	}

	public TikaDocumentReader(String resourceUrl, ExtractedTextFormatter textFormatter) {
		this(new DefaultResourceLoader().getResource(resourceUrl), textFormatter);
	}

	public TikaDocumentReader(Resource resource) {
		this(resource, ExtractedTextFormatter.defaults());
	}

	public TikaDocumentReader(Resource resource, ExtractedTextFormatter textFormatter) {
		this(resource, new BodyContentHandler(), textFormatter);
	}

	public TikaDocumentReader(Resource resource, ContentHandler contentHandler, ExtractedTextFormatter textFormatter) {
		this.parser = new AutoDetectParser();
		this.handler = contentHandler;
		this.metadata = new Metadata();
		this.context = new ParseContext();
		this.resource = resource;
		this.textFormatter = textFormatter;
	}

	@Override
	public List<Document> get() {
		try {
			this.parser.parse(this.resource.getInputStream(), this.handler, this.metadata, this.context);
			return List.of(toDocument(this.handler.toString()));
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Document toDocument(String docText) {
		if (docText == null) {
			docText = "";
		}
		docText = this.textFormatter.format(docText);
		Document doc = new Document(docText);
		doc.getMetadata().put(METADATA_SOURCE, resourceName());
		return doc;
	}

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

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

import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/**
 * Generic document reader, based on Apache Tika, that can extract text from various
 * document formats, including pdf, doc/docx, ppt/pptx. Check the
 * https://tika.apache.org/2.9.0/formats.html for the full list of supported formats.
 *
 * The reader does not provide any addtional pre/post formating for the extracted text.
 * The extracted text is wrapped in a signel {@link Document}.
 *
 * For an advanced PDF document reader consult the PagePdfDocumentReader and
 * PargraphPdfDocumentReader instead.
 *
 * @author Christian Tzolov
 */
public class TikaDocumentReader implements DocumentReader {

	public static final String METADATA_FILE_NAME = "file_name";

	private AutoDetectParser parser;

	private ContentHandler handler;

	private Metadata metadata;

	private ParseContext context;

	private Resource resource;

	public TikaDocumentReader(String resourceUrl) {
		this(new DefaultResourceLoader().getResource(resourceUrl));
	}

	public TikaDocumentReader(Resource resource) {
		this(resource, new BodyContentHandler()); // plain text
	}

	public TikaDocumentReader(Resource resource, ContentHandler contentHandler) {
		this.parser = new AutoDetectParser();
		this.handler = contentHandler;
		this.metadata = new Metadata();
		this.context = new ParseContext();
		this.resource = resource;
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
		Document doc = new Document(docText);
		doc.getMetadata().put(METADATA_FILE_NAME, this.resource.getFilename());
		return doc;
	}

}

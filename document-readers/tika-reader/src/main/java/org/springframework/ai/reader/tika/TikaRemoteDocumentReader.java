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

package org.springframework.ai.reader.tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * A document reader that leverages Apache Tika Server to extract text from a variety of
 * document formats remotely. This reader delegates parsing to a Tika Server instance
 * (e.g. via Docker: {@code docker run -p 9998:9998 apache/tika:latest}).
 *
 * <p>
 * Use this reader when you prefer a remote Tika Server over the embedded
 * {@link TikaDocumentReader}, for example to:
 * </p>
 * <ul>
 * <li>Install additional language support on the server</li>
 * <li>Share a single Tika instance across multiple applications</li>
 * <li>Reduce memory footprint in the calling application</li>
 * </ul>
 *
 * <p>
 * Supported formats: PDF, DOC/DOCX, PPT/PPTX, HTML, and many more. See
 * https://tika.apache.org/3.1.0/formats.html
 * </p>
 *
 * @author Sahil Bhardwaj
 * @since 2.0
 * @see TikaDocumentReader
 * @see <a href="https://cwiki.apache.org/confluence/display/TIKA/TikaServer">Tika Server
 * Documentation</a>
 */
public class TikaRemoteDocumentReader implements DocumentReader {

	/**
	 * Default Tika Server URL when running via Docker.
	 */
	public static final String DEFAULT_TIKA_SERVER_URL = "http://localhost:9998";

	/**
	 * Metadata key representing the source of the document.
	 */
	public static final String METADATA_SOURCE = "source";

	private final String tikaServerUrl;

	private final Resource resource;

	private final ExtractedTextFormatter textFormatter;

	private final RestClient restClient;

	/**
	 * Constructor with Tika Server URL and resource.
	 * @param tikaServerUrl Base URL of the Tika Server (e.g. http://localhost:9998)
	 * @param resource Resource pointing to the document
	 */
	public TikaRemoteDocumentReader(String tikaServerUrl, Resource resource) {
		this(tikaServerUrl, resource, ExtractedTextFormatter.defaults());
	}

	/**
	 * Constructor with Tika Server URL, resource, and text formatter.
	 * @param tikaServerUrl Base URL of the Tika Server (e.g. http://localhost:9998)
	 * @param resource Resource pointing to the document
	 * @param textFormatter Formatter for the extracted text
	 */
	public TikaRemoteDocumentReader(String tikaServerUrl, Resource resource, ExtractedTextFormatter textFormatter) {
		this(tikaServerUrl, resource, textFormatter, RestClient.builder().build());
	}

	/**
	 * Constructor with Tika Server URL, resource, text formatter, and custom RestClient.
	 * @param tikaServerUrl Base URL of the Tika Server (e.g. http://localhost:9998)
	 * @param resource Resource pointing to the document
	 * @param textFormatter Formatter for the extracted text
	 * @param restClient RestClient for HTTP requests (allows custom timeouts, etc.)
	 */
	public TikaRemoteDocumentReader(String tikaServerUrl, Resource resource, ExtractedTextFormatter textFormatter,
			RestClient restClient) {
		Assert.hasText(tikaServerUrl, "Tika Server URL must not be empty");
		Assert.notNull(resource, "Resource must not be null");
		Assert.notNull(textFormatter, "TextFormatter must not be null");
		Assert.notNull(restClient, "RestClient must not be null");
		this.tikaServerUrl = tikaServerUrl.endsWith("/") ? tikaServerUrl.substring(0, tikaServerUrl.length() - 1)
				: tikaServerUrl;
		this.resource = resource;
		this.textFormatter = textFormatter;
		this.restClient = restClient;
	}

	/**
	 * Constructor with resource URL. Uses default Tika Server at localhost:9998.
	 * @param resourceUrl URL to the resource (classpath:, file:, or http:)
	 */
	public TikaRemoteDocumentReader(String resourceUrl) {
		this(DEFAULT_TIKA_SERVER_URL, new DefaultResourceLoader().getResource(resourceUrl));
	}

	/**
	 * Constructor with Tika Server URL and resource URL.
	 * @param tikaServerUrl Base URL of the Tika Server
	 * @param resourceUrl URL to the resource (classpath:, file:, or http:)
	 */
	public TikaRemoteDocumentReader(String tikaServerUrl, String resourceUrl) {
		this(tikaServerUrl, new DefaultResourceLoader().getResource(resourceUrl));
	}

	/**
	 * Constructor with resource. Uses default Tika Server at localhost:9998.
	 * @param resource Resource pointing to the document
	 */
	public TikaRemoteDocumentReader(Resource resource) {
		this(DEFAULT_TIKA_SERVER_URL, resource);
	}

	@Override
	public List<Document> get() {
		try (InputStream stream = this.resource.getInputStream()) {
			byte[] documentBytes = stream.readAllBytes();
			String extractedText = extractTextFromTikaServer(documentBytes);
			return List.of(toDocument(extractedText));
		}
		catch (IOException e) {
			throw new RuntimeException("Failed to read resource: " + this.resource, e);
		}
	}

	private String extractTextFromTikaServer(byte[] documentBytes) {
		String filename = resourceFilename();
		MediaType contentType = guessContentType(filename);

		String responseBody = this.restClient.put()
			.uri(this.tikaServerUrl + "/tika")
			.contentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM)
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
			.header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE)
			.body(documentBytes)
			.retrieve()
			.body(String.class);

		return responseBody != null ? responseBody : "";
	}

	private String resourceFilename() {
		try {
			String name = this.resource.getFilename();
			return StringUtils.hasText(name) ? name : "document";
		}
		catch (Exception e) {
			return "document";
		}
	}

	private @Nullable MediaType guessContentType(String filename) {
		if (filename == null) {
			return null;
		}
		String lower = filename.toLowerCase();
		if (lower.endsWith(".pdf")) {
			return MediaType.APPLICATION_PDF;
		}
		if (lower.endsWith(".docx")) {
			return MediaType.parseMediaType(
					"application/vnd.openxmlformats-officedocument.wordprocessingml.document");
		}
		if (lower.endsWith(".doc")) {
			return MediaType.parseMediaType("application/msword");
		}
		if (lower.endsWith(".pptx")) {
			return MediaType.parseMediaType(
					"application/vnd.openxmlformats-officedocument.presentationml.presentation");
		}
		if (lower.endsWith(".ppt")) {
			return MediaType.parseMediaType("application/vnd.ms-powerpoint");
		}
		if (lower.endsWith(".html") || lower.endsWith(".htm")) {
			return MediaType.TEXT_HTML;
		}
		if (lower.endsWith(".txt")) {
			return MediaType.TEXT_PLAIN;
		}
		return null;
	}

	private Document toDocument(String docText) {
		docText = Objects.requireNonNullElse(docText, "");
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

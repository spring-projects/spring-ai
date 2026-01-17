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

package org.springframework.ai.reader;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

/**
 * A {@link DocumentReader} that reads text from a {@link Resource}.
 *
 * @author Craig Walls
 * @author Christian Tzolov
 */
public class TextReader implements DocumentReader {

	public static final String CHARSET_METADATA = "charset";

	public static final String SOURCE_METADATA = "source";

	/**
	 * Input resource to load the text from.
	 */
	private final Resource resource;

	private final Map<String, Object> customMetadata = new HashMap<>();

	/**
	 * Character set to be used when loading data from the input resource.
	 */
	private Charset charset = StandardCharsets.UTF_8;

	public TextReader(String resourceUrl) {
		this(new DefaultResourceLoader().getResource(resourceUrl));
	}

	public TextReader(Resource resource) {
		Objects.requireNonNull(resource, "The Spring Resource must not be null");
		this.resource = resource;
	}

	public Charset getCharset() {
		return this.charset;
	}

	public void setCharset(Charset charset) {
		Objects.requireNonNull(charset, "The charset must not be null");
		this.charset = charset;
	}

	/**
	 * Metadata associated with all documents created by the loader.
	 * @return Metadata to be assigned to the output Documents.
	 */
	public Map<String, Object> getCustomMetadata() {
		return this.customMetadata;
	}

	@Override
	public List<Document> get() {
		try {

			String document = StreamUtils.copyToString(this.resource.getInputStream(), this.charset);

			// Inject source information as a metadata.
			this.customMetadata.put(CHARSET_METADATA, this.charset.name());
			this.customMetadata.put(SOURCE_METADATA, getResourceIdentifier(this.resource));

			return List.of(new Document(document, this.customMetadata));

		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected String getResourceIdentifier(Resource resource) {
		// Try to get the filename first
		String filename = resource.getFilename();
		if (filename != null && !filename.isEmpty()) {
			return filename;
		}

		// Try to get the URI
		try {
			URI uri = resource.getURI();
			return uri.toString();
		}
		catch (IOException ignored) {
			// If getURI() throws an exception, we'll try the next method
		}

		// Try to get the URL
		try {
			URL url = resource.getURL();
			return url.toString();
		}
		catch (IOException ignored) {
			// If getURL() throws an exception, we'll fall back to getDescription()
		}

		// If all else fails, use the description
		return resource.getDescription();
	}

}

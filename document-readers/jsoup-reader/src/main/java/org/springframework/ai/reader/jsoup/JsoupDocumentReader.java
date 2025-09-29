/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.reader.jsoup;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/**
 * Reads HTML documents and extracts text content using JSoup.
 *
 * This reader provides options for selecting specific HTML elements to extract, handling
 * links, and extracting metadata. It leverages the JSoup library for parsing HTML.
 *
 * @see <a href="https://jsoup.org/">JSoup Website</a>
 * @author Alexandros Pappas
 */
public class JsoupDocumentReader implements DocumentReader {

	private final Resource htmlResource;

	private final JsoupDocumentReaderConfig config;

	public JsoupDocumentReader(String htmlResource) {
		this(new DefaultResourceLoader().getResource(htmlResource));
	}

	public JsoupDocumentReader(Resource htmlResource) {
		this(htmlResource, JsoupDocumentReaderConfig.defaultConfig());
	}

	public JsoupDocumentReader(String htmlResource, JsoupDocumentReaderConfig config) {
		this(new DefaultResourceLoader().getResource(htmlResource), config);
	}

	public JsoupDocumentReader(Resource htmlResource, JsoupDocumentReaderConfig config) {
		this.htmlResource = htmlResource;
		this.config = config;
	}

	@Override
	public List<Document> get() {
		try (InputStream inputStream = this.htmlResource.getInputStream()) {
			org.jsoup.nodes.Document doc = Jsoup.parse(inputStream, this.config.charset, "");

			List<Document> documents = new ArrayList<>();

			if (this.config.allElements) {
				// Extract text from all elements and create a single document
				String allText = doc.body().text(); // .body to exclude head
				Document document = new Document(allText);
				addMetadata(doc, document);
				documents.add(document);
			}
			else if (this.config.groupByElement) {
				// Extract text on a per-element base using the defined selector.
				Elements selectedElements = doc.select(this.config.selector);
				for (Element element : selectedElements) {
					String elementText = element.text();
					Document document = new Document(elementText);
					addMetadata(doc, document);
					// Do not add metadata from element to avoid duplication.
					documents.add(document);
				}
			}
			else {
				// Extract text from specific elements based on the selector
				Elements elements = doc.select(this.config.selector);
				String text = elements.stream().map(Element::text).collect(Collectors.joining(this.config.separator));
				Document document = new Document(text);
				addMetadata(doc, document);
				documents.add(document);
			}

			return documents;

		}
		catch (IOException e) {
			throw new RuntimeException("Failed to read HTML resource: " + this.htmlResource, e);
		}
	}

	private void addMetadata(org.jsoup.nodes.Document jsoupDoc, Document springDoc) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("title", jsoupDoc.title());

		for (String metaTag : this.config.metadataTags) {
			String value = jsoupDoc.select("meta[name=" + metaTag + "]").attr("content");
			if (!value.isEmpty()) {
				metadata.put(metaTag, value);
			}
		}

		if (this.config.includeLinkUrls) {
			Elements links = jsoupDoc.select("a[href]");
			List<String> linkUrls = links.stream().map(link -> link.attr("abs:href")).toList();
			metadata.put("linkUrls", linkUrls);
		}

		// Use putAll to add all entries from additionalMetadata
		metadata.putAll(this.config.additionalMetadata);

		// Add all collected metadata to the Spring Document
		springDoc.getMetadata().putAll(metadata);
	}

}

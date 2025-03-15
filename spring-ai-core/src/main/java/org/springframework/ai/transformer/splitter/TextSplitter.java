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

package org.springframework.ai.transformer.splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

public abstract class TextSplitter implements DocumentTransformer {

	private static final Logger logger = LoggerFactory.getLogger(TextSplitter.class);

	/**
	 * If true the children documents inherit the content-type of the parent they were
	 * split from.
	 */
	private boolean copyContentFormatter = true;

	@Override
	public List<Document> apply(List<Document> documents) {
		return doSplitDocuments(documents);
	}

	public List<Document> split(List<Document> documents) {
		return this.apply(documents);
	}

	public List<Document> split(Document document) {
		return this.apply(List.of(document));
	}

	public boolean isCopyContentFormatter() {
		return this.copyContentFormatter;
	}

	public void setCopyContentFormatter(boolean copyContentFormatter) {
		this.copyContentFormatter = copyContentFormatter;
	}

	private List<Document> doSplitDocuments(List<Document> documents) {
		List<String> texts = new ArrayList<>();
		List<Map<String, Object>> metadataList = new ArrayList<>();
		List<ContentFormatter> formatters = new ArrayList<>();

		for (Document doc : documents) {
			texts.add(doc.getText());
			metadataList.add(doc.getMetadata());
			formatters.add(doc.getContentFormatter());
		}

		return createDocuments(texts, formatters, metadataList);
	}

	private List<Document> createDocuments(List<String> texts, List<ContentFormatter> formatters,
			List<Map<String, Object>> metadataList) {

		// Process the data in a column oriented way and recreate the Document
		List<Document> documents = new ArrayList<>();

		for (int i = 0; i < texts.size(); i++) {
			String text = texts.get(i);
			Map<String, Object> metadata = metadataList.get(i);
			List<String> chunks = splitText(text);
			if (chunks.size() > 1) {
				logger.info("Splitting up document into " + chunks.size() + " chunks.");
			}
			for (String chunk : chunks) {
				// only primitive values are in here -
				Map<String, Object> metadataCopy = metadata.entrySet()
					.stream()
					.filter(e -> e.getKey() != null && e.getValue() != null)
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
				Document newDoc = new Document(chunk, metadataCopy);

				if (this.copyContentFormatter) {
					// Transfer the content-formatter of the parent to the chunked
					// documents it was slit into.
					newDoc.setContentFormatter(formatters.get(i));
				}

				// TODO copy over other properties.
				documents.add(newDoc);
			}
		}
		return documents;
	}

	protected abstract List<String> splitText(String text);

}

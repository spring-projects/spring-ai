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

package org.springframework.ai.loader.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;

/**
 * @author Christian Tzolov
 */
public class MetadataExtractor {

	/**
	 * Enable in place document processing. If false create document copies.
	 */
	private boolean inPlaceProcessing = true;

	/**
	 * Disable the content-formatter template rewrite.
	 */
	private boolean disableTemplateRewrite = false;

	private List<MetadataFeatureExtractor> extractors;

	public MetadataExtractor(List<MetadataFeatureExtractor> extractors) {
		this(extractors, true, false);
	}

	public MetadataExtractor(List<MetadataFeatureExtractor> extractors, boolean inPlaceProcessing,
			boolean disableTemplateRewrite) {
		this.inPlaceProcessing = inPlaceProcessing;
		this.disableTemplateRewrite = disableTemplateRewrite;
		this.extractors = extractors;
	}

	/**
	 * Post process documents chunked from loader. Allows extractors to be chained.
	 * @param documents to post process.
	 * @param contentFormatter Content formatter to override the updated documents.
	 * @return
	 */
	public List<Document> processDocuments(List<Document> documents, ContentFormatter contentFormatter) {

		List<Document> newDocuments = this.inPlaceProcessing ? documents
				: documents.stream().map(doc -> deepCopy(doc)).toList();

		for (MetadataFeatureExtractor extractor : this.extractors) {

			List<Map<String, Object>> metadataList = extractor.extract(newDocuments);

			for (int idx = 0; idx < newDocuments.size(); idx++) {
				newDocuments.get(idx).getMetadata().putAll(metadataList.get(idx));
			}
		}

		if (contentFormatter != null) {

			newDocuments.forEach(document -> {
				// Update formatter
				if (document.getContentFormatter() instanceof DefaultContentFormatter
						&& contentFormatter instanceof DefaultContentFormatter) {

					DefaultContentFormatter docFormatter = (DefaultContentFormatter) document.getContentFormatter();
					DefaultContentFormatter toUpdateFormatter = (DefaultContentFormatter) contentFormatter;

					var updatedEmbedExcludeKeys = new ArrayList<>(docFormatter.getExcludedEmbedMetadataKeys());
					updatedEmbedExcludeKeys.addAll(toUpdateFormatter.getExcludedEmbedMetadataKeys());

					var updatedLlmExcludeKeys = new ArrayList<>(docFormatter.getExcludedInferenceMetadataKeys());
					updatedLlmExcludeKeys.addAll(toUpdateFormatter.getExcludedInferenceMetadataKeys());

					var builder = DefaultContentFormatter.builder()
						.withExcludedEmbedMetadataKeys(updatedEmbedExcludeKeys)
						.withExcludedLlmMetadataKeys(updatedLlmExcludeKeys)
						.withMetadataTemplate(docFormatter.getMetadataTemplate())
						.withMetadataSeparator(docFormatter.getMetadataSeparator());

					if (!this.disableTemplateRewrite) {
						builder.withTextTemplate(docFormatter.getTextTemplate());
					}
					document.setContentFormatter(builder.build());
				}
				else {
					// Override formatter
					document.setContentFormatter(contentFormatter);
				}
			});
		}

		return newDocuments;
	}

	private Document deepCopy(Document document) {
		Document newDoc = new Document(document.getId(), document.getContent(), document.getMetadata());
		newDoc.setContentFormatter(document.getContentFormatter());
		return newDoc;
	}

}

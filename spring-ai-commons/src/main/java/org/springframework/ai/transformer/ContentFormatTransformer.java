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

package org.springframework.ai.transformer;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.util.Assert;

/**
 * ContentFormatTransformer processes a list of documents by applying a content formatter
 * to each document.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ContentFormatTransformer implements DocumentTransformer {

	/**
	 * Disable the content-formatter template rewrite.
	 */
	private final boolean disableTemplateRewrite;

	private final ContentFormatter contentFormatter;

	/**
	 * Creates a ContentFormatTransformer object with the given ContentFormatter.
	 * @param contentFormatter the ContentFormatter to be used for transforming the
	 * documents
	 */
	public ContentFormatTransformer(ContentFormatter contentFormatter) {
		this(contentFormatter, false);
	}

	/**
	 * The ContentFormatTransformer class is responsible for processing a list of
	 * documents by applying a content formatter to each document.
	 * @param contentFormatter The ContentFormatter to be used for transforming the
	 * documents
	 * @param disableTemplateRewrite Flag indicating whether to disable the
	 * content-formatter template rewrite
	 */
	public ContentFormatTransformer(ContentFormatter contentFormatter, boolean disableTemplateRewrite) {
		Assert.notNull(contentFormatter, "ContentFormatter is required");
		this.contentFormatter = contentFormatter;
		this.disableTemplateRewrite = disableTemplateRewrite;
	}

	/**
	 * Post process documents chunked from loader. Allows extractors to be chained.
	 * @param documents to post process.
	 * @return processed documents
	 */
	public List<Document> apply(List<Document> documents) {
		documents.forEach(this::processDocument);
		return documents;
	}

	private void processDocument(Document document) {
		if (document.getContentFormatter() instanceof DefaultContentFormatter docFormatter
				&& this.contentFormatter instanceof DefaultContentFormatter toUpdateFormatter) {
			updateFormatter(document, docFormatter, toUpdateFormatter);

		}
		else {
			overrideFormatter(document);
		}
	}

	private void updateFormatter(Document document, DefaultContentFormatter docFormatter,
			DefaultContentFormatter toUpdateFormatter) {
		List<String> updatedEmbedExcludeKeys = new ArrayList<>(docFormatter.getExcludedEmbedMetadataKeys());
		updatedEmbedExcludeKeys.addAll(toUpdateFormatter.getExcludedEmbedMetadataKeys());

		List<String> updatedInterfaceExcludeKeys = new ArrayList<>(docFormatter.getExcludedInferenceMetadataKeys());
		updatedInterfaceExcludeKeys.addAll(toUpdateFormatter.getExcludedInferenceMetadataKeys());

		DefaultContentFormatter.Builder builder = DefaultContentFormatter.builder()
			.withExcludedEmbedMetadataKeys(updatedEmbedExcludeKeys)
			.withExcludedInferenceMetadataKeys(updatedInterfaceExcludeKeys)
			.withMetadataTemplate(docFormatter.getMetadataTemplate())
			.withMetadataSeparator(docFormatter.getMetadataSeparator());

		if (!this.disableTemplateRewrite) {
			builder.withTextTemplate(docFormatter.getTextTemplate());
		}

		document.setContentFormatter(builder.build());
	}

	private void overrideFormatter(Document document) {
		document.setContentFormatter(this.contentFormatter);
	}

}

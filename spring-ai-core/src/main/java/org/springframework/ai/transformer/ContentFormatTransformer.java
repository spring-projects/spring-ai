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

package org.springframework.ai.transformer;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

/**
 * @author Christian Tzolov
 */
public class ContentFormatTransformer implements DocumentTransformer {

	/**
	 * Disable the content-formatter template rewrite.
	 */
	private boolean disableTemplateRewrite = false;

	private ContentFormatter contentFormatter;

	public ContentFormatTransformer(ContentFormatter contentFormatter) {
		this(contentFormatter, false);
	}

	public ContentFormatTransformer(ContentFormatter contentFormatter, boolean disableTemplateRewrite) {
		this.contentFormatter = contentFormatter;
		this.disableTemplateRewrite = disableTemplateRewrite;
	}

	/**
	 * Post process documents chunked from loader. Allows extractors to be chained.
	 * @param documents to post process.
	 * @return
	 */
	public List<Document> apply(List<Document> documents) {
		if (contentFormatter != null) {
			documents.forEach(this::processDocument);
		}

		return documents;
	}

	private void processDocument(Document document) {
		if (document.getContentFormatter() instanceof DefaultContentFormatter docFormatter &&
				contentFormatter instanceof DefaultContentFormatter toUpdateFormatter) {
			updateFormatter(document, docFormatter, toUpdateFormatter);

		} else {
			overrideFormatter(document);
		}
	}

	private void updateFormatter(Document document, DefaultContentFormatter docFormatter, DefaultContentFormatter toUpdateFormatter) {
		List<String> updatedEmbedExcludeKeys = new ArrayList<>(docFormatter.getExcludedEmbedMetadataKeys());
		updatedEmbedExcludeKeys.addAll(toUpdateFormatter.getExcludedEmbedMetadataKeys());

		List<String> updatedInterfaceExcludeKeys = new ArrayList<>(docFormatter.getExcludedInferenceMetadataKeys());
		updatedInterfaceExcludeKeys.addAll(toUpdateFormatter.getExcludedInferenceMetadataKeys());

		DefaultContentFormatter.Builder builder = DefaultContentFormatter.builder()
				.withExcludedEmbedMetadataKeys(updatedEmbedExcludeKeys)
				.withExcludedInferenceMetadataKeys(updatedInterfaceExcludeKeys)
				.withMetadataTemplate(docFormatter.getMetadataTemplate())
				.withMetadataSeparator(docFormatter.getMetadataSeparator());

		if (!disableTemplateRewrite) {
			builder.withTextTemplate(docFormatter.getTextTemplate());
		}

		document.setContentFormatter(builder.build());
	}

	private void overrideFormatter(Document document) {
		document.setContentFormatter(contentFormatter);
	}
}

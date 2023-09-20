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

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

/**
 * @author Christian Tzolov
 */
public abstract class AbstractMetadataFeatureExtractor implements DocumentTransformer {

	@Override
	public List<Document> apply(List<Document> documents) {
		List<Map<String, Object>> metadataList = this.extract(documents);

		for (int idx = 0; idx < documents.size(); idx++) {
			documents.get(idx).getMetadata().putAll(metadataList.get(idx));
		}

		return documents;
	}

	/**
	 * Extracts metadata for a list of documents, returning a list of metadata
	 * dictionaries corresponding to each document.
	 * @param documents Documents to extract metadata from.
	 * @return List of metadata dictionaries corresponding to each document
	 */
	abstract public List<Map<String, Object>> extract(List<Document> documents);

}

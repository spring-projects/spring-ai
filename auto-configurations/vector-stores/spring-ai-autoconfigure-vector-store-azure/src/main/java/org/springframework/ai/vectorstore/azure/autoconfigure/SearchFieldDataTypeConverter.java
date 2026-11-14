/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.vectorstore.azure.autoconfigure;

import com.azure.search.documents.indexes.models.SearchFieldDataType;

import org.springframework.core.convert.converter.Converter;

/**
 * Converts a {@link SearchFieldDataType} from its configuration value. Accepts the
 * short names used in configuration (for example {@code string} or {@code int32}), the
 * canonical Azure values (for example {@code Edm.String}), and collection types (for
 * example {@code Collection(Edm.String)}). Unknown values are passed through to
 * {@link SearchFieldDataType#fromString(String)} so the type stays expandable.
 *
 * @author Aditya Vijay
 * @since 2.1.0
 */
public class SearchFieldDataTypeConverter implements Converter<String, SearchFieldDataType> {

	private static final String COLLECTION_PREFIX = "Collection(";

	@Override
	public SearchFieldDataType convert(String source) {
		String value = source.trim();

		if (value.regionMatches(true, 0, COLLECTION_PREFIX, 0, COLLECTION_PREFIX.length())
				&& value.endsWith(")")) {
			String inner = value.substring(COLLECTION_PREFIX.length(), value.length() - 1);
			return SearchFieldDataType.collection(convert(inner));
		}

		for (SearchFieldDataType known : SearchFieldDataType.values()) {
			if (known.toString().equalsIgnoreCase(value)) {
				return known;
			}
			String shortName = known.toString().startsWith("Edm.") ? known.toString().substring(4)
					: known.toString();
			if (shortName.equalsIgnoreCase(value)) {
				return known;
			}
		}

		return SearchFieldDataType.fromString(value);
	}

}

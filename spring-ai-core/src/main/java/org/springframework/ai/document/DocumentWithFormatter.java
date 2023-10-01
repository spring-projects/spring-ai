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

package org.springframework.ai.document;

import java.util.Map;

import org.springframework.util.Assert;

/**
 * Document decorator that uses an ephemeral {@link ContentFormatter} instance for the
 * {@link #getContent()}.
 *
 * @author Christian Tzolov
 */
public class DocumentWithFormatter extends Document {

	/**
	 * Ephemeral, content formatter to use by default on getContent().
	 */
	private final ContentFormatter contentFormatter;

	public DocumentWithFormatter(Document document, ContentFormatter contentFormatter) {
		super(document.getId(), document.getContent(), document.getMetadata());

		Assert.notNull(contentFormatter, "ContentFormatter must not be null");
		this.contentFormatter = contentFormatter;
	}

	public DocumentWithFormatter(String id, String content, Map<String, Object> metadata,
			ContentFormatter contentFormatter) {
		super(id, content, metadata);

		Assert.notNull(contentFormatter, "ContentFormatter must not be null");
		this.contentFormatter = contentFormatter;
	}

	@Override
	public String getContent() {
		return this.getContent(this.contentFormatter);
	}

}

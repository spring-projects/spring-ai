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

package org.springframework.ai.vectorstore.weaviate;

import org.springframework.util.Assert;

/**
 * Provided Weaviate vector option configuration.
 *
 * @author Jonghoon Park
 * @since 1.1.0
 */
public class WeaviateVectorStoreOptions {

	private String objectClass = "SpringAiWeaviate";

	private String contentFieldName = "content";

	private String metaFieldPrefix = "meta_";

	public String getObjectClass() {
		return this.objectClass;
	}

	public void setObjectClass(String objectClass) {
		Assert.hasText(objectClass, "objectClass cannot be null or empty");
		this.objectClass = objectClass;
	}

	public String getContentFieldName() {
		return this.contentFieldName;
	}

	public void setContentFieldName(String contentFieldName) {
		Assert.hasText(contentFieldName, "contentFieldName cannot be null or empty");
		this.contentFieldName = contentFieldName;
	}

	public String getMetaFieldPrefix() {
		return this.metaFieldPrefix;
	}

	public void setMetaFieldPrefix(String metaFieldPrefix) {
		Assert.notNull(metaFieldPrefix, "metaFieldPrefix can be empty but not null");
		this.metaFieldPrefix = metaFieldPrefix;
	}

}

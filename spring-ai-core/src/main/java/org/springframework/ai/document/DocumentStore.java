/*
 * Copyright 2016 the original author or authors.
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

import java.util.List;
import java.util.Map;

/**
 * Interface for a document storage system that can retrieve documents by their IDs.
 */
public interface DocumentStore extends Map<String, Document> {

	/**
	 * Retrieves a list of documents by their IDs.
	 *
	 * @param ids The list of document IDs to retrieve.
	 * @return The list of retrieved documents.
	 */
	List<Document> get(List<String> ids);
}

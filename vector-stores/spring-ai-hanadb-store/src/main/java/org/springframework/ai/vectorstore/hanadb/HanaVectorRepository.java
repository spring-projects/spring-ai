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

package org.springframework.ai.vectorstore.hanadb;

import java.util.List;

/**
 * The {@code HanaVectorRepository} interface provides methods for interacting with a HANA
 * vector repository, which allows storing and querying vector embeddings. The repository
 * is generic and can work with any entity that extends the {@link HanaVectorEntity}
 * class.
 *
 * @param <T> The type of entity that extends {@link HanaVectorEntity}.
 * @author Rahul Mittal
 * @since 1.0.0
 */
public interface HanaVectorRepository<T extends HanaVectorEntity> {

	void save(String tableName, String id, String embedding, String content);

	int deleteEmbeddingsById(String tableName, List<String> idList);

	int deleteAllEmbeddings(String tableName);

	List<T> cosineSimilaritySearch(String tableName, int topK, String queryEmbedding);

}

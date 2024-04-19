/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Rahul Mittal
 * @since 1.0.0
 */
@Repository
public class CricketWorldCupRepository implements HanaVectorRepository<CricketWorldCup> {

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	@Transactional
	public void save(String tableName, String id, String embedding, String content) {
		String sql = String.format("""
				INSERT INTO %s (_ID, EMBEDDING, CONTENT)
				VALUES(:_id, TO_REAL_VECTOR(:embedding), :content)
				""", tableName);

		entityManager.createNativeQuery(sql)
			.setParameter("_id", id)
			.setParameter("embedding", embedding)
			.setParameter("content", content)
			.executeUpdate();
	}

	@Override
	@Transactional
	public int deleteEmbeddingsById(String tableName, List<String> idList) {
		String sql = String.format("""
				DELETE FROM %s WHERE _ID IN (:ids)
				""", tableName);

		return entityManager.createNativeQuery(sql).setParameter("ids", idList).executeUpdate();
	}

	@Override
	@Transactional
	public int deleteAllEmbeddings(String tableName) {
		String sql = String.format("""
				DELETE FROM %s
				""", tableName);

		return entityManager.createNativeQuery(sql).executeUpdate();
	}

	@Override
	public List<CricketWorldCup> cosineSimilaritySearch(String tableName, int topK, String queryEmbedding) {
		String sql = String.format("""
				SELECT TOP :topK * FROM %s
				ORDER BY COSINE_SIMILARITY(EMBEDDING, TO_REAL_VECTOR(:queryEmbedding)) DESC
				""", tableName);

		return entityManager.createNativeQuery(sql, CricketWorldCup.class)
			.setParameter("topK", topK)
			.setParameter("queryEmbedding", queryEmbedding)
			.getResultList();
	}

}

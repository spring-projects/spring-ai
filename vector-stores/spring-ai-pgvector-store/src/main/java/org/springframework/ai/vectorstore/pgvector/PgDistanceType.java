package org.springframework.ai.vectorstore.pgvector;

public record PgDistanceType(String name, String operator, String index, String similaritySearchSqlTemplate) {
}

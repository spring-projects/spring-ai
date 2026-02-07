package org.springframework.ai.vectorstore.pgvector;

public record PgVectorColumnNames(String id, String content, String metadata, String embedding) {

	public static final PgVectorColumnNames DEFAULT =
			new PgVectorColumnNames("id", "content", "metadata", "embedding");

	public PgVectorColumnNames(String id, String content, String metadata, String embedding) {
		this.id = validate(id);
		this.content = validate(content);
		this.metadata = validate(metadata);
		this.embedding = validate(embedding);
	}

	public static PgVectorColumnNamesBuilder builder() {
		return new PgVectorColumnNamesBuilder();
	}


	private static String validate(String v) {
		if (v == null || !PgVectorSchemaValidator.isValidNameForDatabaseObject(v)) {
			throw new IllegalArgumentException("Invalid SQL identifier: " + v);
		}
		return v;
	}

	public static final class PgVectorColumnNamesBuilder {
		private String id = "id";
		private String content = "content";
		private String metadata = "metadata";
		private String embedding = "embedding";

		public PgVectorColumnNamesBuilder id(String v) {
			this.id = v;
			return this;
		}

		public PgVectorColumnNamesBuilder content(String v) {
			this.content = v;
			return this;
		}

		public PgVectorColumnNamesBuilder metadata(String v) {
			this.metadata = v;
			return this;
		}

		public PgVectorColumnNamesBuilder embedding(String v) {
			this.embedding = v;
			return this;
		}

		public PgVectorColumnNames build() {
			return new PgVectorColumnNames(id, content, metadata, embedding);
		}
	}
}

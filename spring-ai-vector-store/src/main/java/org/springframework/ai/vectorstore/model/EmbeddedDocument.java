package org.springframework.ai.vectorstore.model;

import org.springframework.ai.document.Document;
import org.springframework.util.Assert;

public record EmbeddedDocument(Document document, float[] embedding) {
	public EmbeddedDocument {
		Assert.notNull(document, "Document cannot be null.");
		Assert.notNull(embedding, "Embedding cannot be null.");
	}
}

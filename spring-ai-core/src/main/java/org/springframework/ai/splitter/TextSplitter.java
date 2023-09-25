package org.springframework.ai.splitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class TextSplitter implements DocumentTransformer {

	private static final Logger logger = LoggerFactory.getLogger(TextSplitter.class);

	@Override
	public List<Document> apply(List<Document> documents) {
		return doSplitDocuments(documents);
	}

	private List<Document> doSplitDocuments(List<Document> documents) {
		List<String> texts = new ArrayList<>();
		Map<String, Object> metadata = new HashMap<>();

		for (Document doc : documents) {
			texts.add(doc.getText());
			metadata.putAll(doc.getMetadata());
		}

		return createDocuments(texts, metadata);
	}

	private List<Document> createDocuments(List<String> texts, Map<String, Object> metadata) {

		// Process the data in a column oriented way and recreate the Document
		List<Document> documents = new ArrayList<>();

		for (int i = 0; i < texts.size(); i++) {
			String text = texts.get(i);
			List<String> chunks = splitText(text);
			if (chunks.size() > 1) {
				logger.info("Splitting up document into " + chunks.size() + " chunks.");
			}
			for (String chunk : chunks) {
				// only primitive values are in here -
				Map<String, Object> metadataCopy = metadata.entrySet()
					.stream()
					.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
				Document newDoc = new Document(chunk, metadataCopy);
				// TODO copy over other properties.
				documents.add(newDoc);
			}
		}
		return documents;
	}

	protected abstract List<String> splitText(String text);

}

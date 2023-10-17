package org.springframework.ai.transformer.splitter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class TextSplitter implements DocumentTransformer {

	private static final Logger logger = LoggerFactory.getLogger(TextSplitter.class);

	/**
	 * If true the children documents inherit the content-type of the parent they were
	 * split from.
	 */
	private boolean copyContentFormatter = true;

	@Override
	public List<Document> apply(List<Document> documents) {
		return doSplitDocuments(documents);
	}

	public void setCopyContentFormatter(boolean copyContentFormatter) {
		this.copyContentFormatter = copyContentFormatter;
	}

	public boolean isCopyContentFormatter() {
		return this.copyContentFormatter;
	}

	private List<Document> doSplitDocuments(List<Document> documents) {
		List<String> texts = new ArrayList<>();
		Map<String, Object> metadata = new HashMap<>();
		List<ContentFormatter> formatters = new ArrayList<>();

		for (Document doc : documents) {
			texts.add(doc.getContent());
			metadata.putAll(doc.getMetadata());
			formatters.add(doc.getContentFormatter());
		}

		return createDocuments(texts, formatters, metadata);
	}

	private List<Document> createDocuments(List<String> texts, List<ContentFormatter> formatters,
			Map<String, Object> metadata) {

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

				if (this.copyContentFormatter) {
					// Transfer the content-formatter of the parent to the chunked
					// documents it was slit into.
					newDoc.setContentFormatter(formatters.get(i));
				}

				// TODO copy over other properties.
				documents.add(newDoc);
			}
		}
		return documents;
	}

	protected abstract List<String> splitText(String text);

}

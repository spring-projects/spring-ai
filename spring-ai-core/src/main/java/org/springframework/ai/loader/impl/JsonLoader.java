package org.springframework.ai.loader.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.loader.Loader;
import org.springframework.ai.splitter.TextSplitter;
import org.springframework.ai.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.*;

public class JsonLoader implements Loader {

	/**
	 * The key from the JSON that we will use as the text to parse into the Document text
	 */
	private String textKey = "text";

	private Resource resource;

	public JsonLoader(Resource resource) {
		Objects.requireNonNull(this.resource, "The Spring Resource must not be null");
		this.resource = resource;
	}

	public JsonLoader(String textKey, Resource resource) {
		Objects.requireNonNull(textKey, "textKey must not be null");
		Objects.requireNonNull(resource, "The Spring Resource must not be null");
		this.textKey = textKey;
		this.resource = resource;
	}

	@Override
	public List<Document> load() {
		return load(new TokenTextSplitter());
	}

	@Override
	public List<Document> load(TextSplitter textSplitter) {

		ObjectMapper objectMapper = new ObjectMapper();
		TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {
		};

		List<Document> documents = new ArrayList<>();
		try {
			// TODO, not all json will be an array
			List<Map<String, Object>> jsonData = objectMapper.readValue(this.resource.getInputStream(),
					new TypeReference<List<Map<String, Object>>>() {
					});
			for (Map<String, Object> item : jsonData) {
				if (item.containsKey(this.textKey)) {
					Document document = new Document(item.get(this.textKey).toString());
					documents.add(document);
				}
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		List<Document> splitDocuments = textSplitter.apply(documents);
		return splitDocuments;
	}

}

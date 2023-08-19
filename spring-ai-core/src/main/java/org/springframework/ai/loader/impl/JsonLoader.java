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
	private List<String> jsonKeysToUse = new ArrayList<>();

	private Resource resource;

	public JsonLoader(Resource resource) {
		Objects.requireNonNull(resource, "The Spring Resource must not be null");
		this.resource = resource;
	}

	public JsonLoader(Resource resource, String... jsonKeysToUse) {
		Objects.requireNonNull(jsonKeysToUse, "keys must not be null");
		Objects.requireNonNull(resource, "The Spring Resource must not be null");
		this.jsonKeysToUse = List.of(jsonKeysToUse);
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
				StringBuilder sb = new StringBuilder();
				for (String key : jsonKeysToUse) {
					if (item.containsKey(key)) {
						sb.append(key);
						sb.append(": ");
						sb.append(item.get(key));
						sb.append(System.lineSeparator());
					}
				}

				if (!sb.isEmpty()) {
					Document document = new Document(sb.toString());
					documents.add(document);
				}
				else {
					Document document = new Document(item.toString());
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

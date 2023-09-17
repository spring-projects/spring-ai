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

	private Resource resource;

	private JsonMetadataGenerator jsonMetadataGenerator;

	/**
	 * The key from the JSON that we will use as the text to parse into the Document text
	 */
	private List<String> jsonKeysToUse;

	public JsonLoader(Resource resource) {
		this(resource, new ArrayList<>().toArray(new String[0]));
	}

	public JsonLoader(Resource resource, String... jsonKeysToUse) {
		this(resource, new EmptyJsonMetadataGenerator(), jsonKeysToUse);
	}

	public JsonLoader(Resource resource, JsonMetadataGenerator jsonMetadataGenerator, String... jsonKeysToUse) {
		Objects.requireNonNull(jsonKeysToUse, "keys must not be null");
		Objects.requireNonNull(jsonMetadataGenerator, "jsonMetadataGenerator must not be null");
		Objects.requireNonNull(resource, "The Spring Resource must not be null");
		this.resource = resource;
		this.jsonMetadataGenerator = jsonMetadataGenerator;
		this.jsonKeysToUse = List.of(jsonKeysToUse);
	}

	@Override
	public List<Document> load() {
		return load(new TokenTextSplitter());
	}

	@Override
	public List<Document> load(TextSplitter textSplitter) {

		ObjectMapper objectMapper = new ObjectMapper();
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

				Map<String, Object> metadata = this.jsonMetadataGenerator.generate(item);

				Document document;
				if (!sb.isEmpty()) {
					document = new Document(sb.toString(), metadata);
				}
				else {
					document = new Document(item.toString(), metadata);
				}
				// Splitting at the item level is good when the size of the json per
				// element is large
				// as is the case with a catalog of product, as the metadata applies
				// across all split documents
				// This may not be good when the size of the json element is small as it
				// can create too many individual
				// documents.
				List<Document> splitDocuments = textSplitter.apply(List.of(document));
				documents.addAll(splitDocuments);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return documents;
	}

}

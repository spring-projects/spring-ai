package org.springframework.ai.reader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;

public class JsonReader implements DocumentReader {

	private Resource resource;

	private JsonMetadataGenerator jsonMetadataGenerator;

	/**
	 * The key from the JSON that we will use as the text to parse into the Document text
	 */
	private List<String> jsonKeysToUse;

	public JsonReader(Resource resource) {
		this(resource, new ArrayList<>().toArray(new String[0]));
	}

	public JsonReader(Resource resource, String... jsonKeysToUse) {
		this(resource, new EmptyJsonMetadataGenerator(), jsonKeysToUse);
	}

	public JsonReader(Resource resource, JsonMetadataGenerator jsonMetadataGenerator, String... jsonKeysToUse) {
		Objects.requireNonNull(jsonKeysToUse, "keys must not be null");
		Objects.requireNonNull(jsonMetadataGenerator, "jsonMetadataGenerator must not be null");
		Objects.requireNonNull(resource, "The Spring Resource must not be null");
		this.resource = resource;
		this.jsonMetadataGenerator = jsonMetadataGenerator;
		this.jsonKeysToUse = List.of(jsonKeysToUse);
	}

	@Override
	public List<Document> get() {
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

				documents.add(document);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return documents;
	}

}

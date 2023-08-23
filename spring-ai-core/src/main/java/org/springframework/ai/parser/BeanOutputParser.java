package org.springframework.ai.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import org.springframework.ai.prompt.PromptTemplate;

import java.util.Map;
import java.util.Objects;

public class BeanOutputParser<T> implements OutputParser<T> {

	private String jsonSchema;

	private Class clazz;

	private ObjectMapper objectMapper;

	public BeanOutputParser(Class<T> clazz) {
		Objects.requireNonNull(clazz, "Java Class can not be null;");
		this.clazz = clazz;
		this.objectMapper = getObjectMapper();
		generateSchema();
	}

	public BeanOutputParser(Class<T> clazz, ObjectMapper objectMapper) {
		Objects.requireNonNull(clazz, "Java Class can not be null;");
		Objects.requireNonNull(objectMapper, "ObjectMapper can not be null;");
		this.clazz = clazz;
		this.objectMapper = objectMapper;
		generateSchema();
	}

	private void generateSchema() {
		SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12,
				OptionPreset.PLAIN_JSON);
		SchemaGeneratorConfig config = configBuilder.build();
		SchemaGenerator generator = new SchemaGenerator(config);
		JsonNode jsonSchema = generator.generateSchema(this.clazz);
		this.jsonSchema = jsonSchema.toPrettyString();
	}

	@Override
	public T parse(String text) {
		try {
			return (T) this.objectMapper.readValue(text, this.clazz);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	protected ObjectMapper getObjectMapper() {
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return objectMapper;
	}

	@Override
	public String getFormat() {

		String raw = """
				Your response should be in JSON format.
				Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
				Here is the JSON Schema instance your output must adhere to:
				```{jsonSchema}```
				""";
		PromptTemplate promptTemplate = new PromptTemplate(raw);
		return promptTemplate.render(Map.of("jsonSchema", this.jsonSchema));
	}

}

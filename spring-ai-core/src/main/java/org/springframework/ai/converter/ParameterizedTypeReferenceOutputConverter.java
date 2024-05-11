/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.converter;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.NonNull;
import java.lang.reflect.Type;

import static com.github.victools.jsonschema.generator.OptionPreset.PLAIN_JSON;
import static com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_2020_12;

/**
 * An implementation of {@link StructuredOutputConverter} that transforms the LLM output
 * to a specific object type using JSON schema. This parser works by generating a JSON
 * schema based on a given Java class type reference, which is then used to validate and
 * transform the LLM output into the desired type.
 *
 * @param <T> The target type to which the output will be converted.
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Sebastian Ullrich
 * @author Kirk Lund
 * @author Josh Long
 */
public class ParameterizedTypeReferenceOutputConverter<T> implements StructuredOutputConverter<T> {

	/** Holds the generated JSON schema for the target type. */
	private String jsonSchema;

	/**
	 * The target class type reference to which the output will be converted.
	 */
	@SuppressWarnings({ "FieldMayBeFinal", "rawtypes" })
	private TypeReference<T> typeRef;

	/** The object mapper used for deserialization and other JSON operations. */
	@SuppressWarnings("FieldMayBeFinal")
	private ObjectMapper objectMapper;

	/**
	 * Constructor to initialize with the target class type reference.
	 * @param typeRef The target type's class.
	 */
	public ParameterizedTypeReferenceOutputConverter(ParameterizedTypeReference<T> typeRef) {
		this(new CustomizedTypeReference<>(typeRef), null);
	}

	/**
	 * Constructor to initialize with the target class type reference, a custom object
	 * mapper, and a line endings normalizer to ensure consistent line endings on any
	 * platform.
	 * @param typeRef The target class type reference.
	 * @param objectMapper Custom object mapper for JSON operations. endings.
	 */
	public ParameterizedTypeReferenceOutputConverter(ParameterizedTypeReference<T> typeRef, ObjectMapper objectMapper) {
		this(new CustomizedTypeReference<>(typeRef), objectMapper);
	}

	private static class CustomizedTypeReference<T> extends TypeReference<T> {

		private final Type type;

		CustomizedTypeReference(ParameterizedTypeReference<T> typeRef) {
			this.type = typeRef.getType();
		}

		@Override
		public Type getType() {
			return this.type;
		}

	}

	/**
	 * Constructor to initialize with the target class type reference, a custom object
	 * mapper, and a line endings normalizer to ensure consistent line endings on any
	 * platform.
	 * @param typeRef The target class type reference.
	 * @param objectMapper Custom object mapper for JSON operations. endings.
	 */
	private ParameterizedTypeReferenceOutputConverter(TypeReference<T> typeRef, ObjectMapper objectMapper) {
		Objects.requireNonNull(typeRef, "Type reference cannot be null;");
		this.typeRef = typeRef;
		this.objectMapper = objectMapper != null ? objectMapper : getObjectMapper();
		generateSchema();
	}

	/**
	 * Generates the JSON schema for the target type.
	 */
	private void generateSchema() {
		JacksonModule jacksonModule = new JacksonModule();
		SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(DRAFT_2020_12, PLAIN_JSON)
			.with(jacksonModule);
		SchemaGeneratorConfig config = configBuilder.build();
		SchemaGenerator generator = new SchemaGenerator(config);
		JsonNode jsonNode = generator.generateSchema(this.typeRef.getType());
		ObjectWriter objectWriter = new ObjectMapper().writer(new DefaultPrettyPrinter()
			.withObjectIndenter(new DefaultIndenter().withLinefeed(System.lineSeparator())));
		try {
			this.jsonSchema = objectWriter.writeValueAsString(jsonNode);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Could not pretty print json schema for " + this.typeRef, e);
		}
	}

	@Override
	/**
	 * Parses the given text to transform it to the desired target type.
	 * @param text The LLM output in string format.
	 * @return The parsed output in the desired target type.
	 */
	public T convert(@NonNull String text) {
		try {
			return (T) this.objectMapper.readValue(text, this.typeRef);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Configures and returns an object mapper for JSON operations.
	 * @return Configured object mapper.
	 */
	protected ObjectMapper getObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper;
	}

	/**
	 * Provides the expected format of the response, instructing that it should adhere to
	 * the generated JSON schema.
	 * @return The instruction format string.
	 */
	@Override
	public String getFormat() {
		String template = """
				Your response should be in JSON format.
				Do not include any explanations, only provide a RFC8259 compliant JSON response following this format without deviation.
				Do not include markdown code blocks in your response.
				Remove the ```json markdown from the output.
				Here is the JSON Schema instance your output must adhere to:
				```%s```
				""";
		return String.format(template, this.jsonSchema);
	}

}

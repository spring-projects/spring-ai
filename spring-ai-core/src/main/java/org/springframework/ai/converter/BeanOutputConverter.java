/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.converter;

import java.lang.reflect.Type;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.NonNull;

/**
 * An implementation of {@link StructuredOutputConverter} that transforms the LLM output
 * to a specific object type using JSON schema. This converter works by generating a JSON
 * schema based on a given Java class or parameterized type reference, which is then used
 * to validate and transform the LLM output into the desired type.
 *
 * @param <T> The target type to which the output will be converted.
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Sebastian Ullrich
 * @author Kirk Lund
 * @author Josh Long
 * @author Sebastien Deleuze
 * @author Soby Chacko
 */
public class BeanOutputConverter<T> implements StructuredOutputConverter<T> {

	private final Logger logger = LoggerFactory.getLogger(BeanOutputConverter.class);

	/**
	 * The target class type reference to which the output will be converted.
	 */
	private final Type type;

	/** The object mapper used for deserialization and other JSON operations. */
	private final ObjectMapper objectMapper;

	/** Holds the generated JSON schema for the target type. */
	private String jsonSchema;

	/**
	 * Constructor to initialize with the target type's class.
	 * @param clazz The target type's class.
	 */
	public BeanOutputConverter(Class<T> clazz) {
		this(ParameterizedTypeReference.forType(clazz));
	}

	/**
	 * Constructor to initialize with the target type's class, a custom object mapper, and
	 * a line endings normalizer to ensure consistent line endings on any platform.
	 * @param clazz The target type's class.
	 * @param objectMapper Custom object mapper for JSON operations. endings.
	 */
	public BeanOutputConverter(Class<T> clazz, ObjectMapper objectMapper) {
		this(ParameterizedTypeReference.forType(clazz), objectMapper);
	}

	/**
	 * Constructor to initialize with the target class type reference.
	 * @param typeRef The target class type reference.
	 */
	public BeanOutputConverter(ParameterizedTypeReference<T> typeRef) {
		this(typeRef.getType(), null);
	}

	/**
	 * Constructor to initialize with the target class type reference, a custom object
	 * mapper, and a line endings normalizer to ensure consistent line endings on any
	 * platform.
	 * @param typeRef The target class type reference.
	 * @param objectMapper Custom object mapper for JSON operations. endings.
	 */
	public BeanOutputConverter(ParameterizedTypeReference<T> typeRef, ObjectMapper objectMapper) {
		this(typeRef.getType(), objectMapper);
	}

	/**
	 * Constructor to initialize with the target class type reference, a custom object
	 * mapper, and a line endings normalizer to ensure consistent line endings on any
	 * platform.
	 * @param type The target class type.
	 * @param objectMapper Custom object mapper for JSON operations. endings.
	 */
	private BeanOutputConverter(Type type, ObjectMapper objectMapper) {
		Objects.requireNonNull(type, "Type cannot be null;");
		this.type = type;
		this.objectMapper = objectMapper != null ? objectMapper : getObjectMapper();
		generateSchema();
	}

	/**
	 * Generates the JSON schema for the target type.
	 */
	private void generateSchema() {
		JacksonModule jacksonModule = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
				JacksonOption.RESPECT_JSONPROPERTY_ORDER);
		SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
				com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_2020_12,
				com.github.victools.jsonschema.generator.OptionPreset.PLAIN_JSON)
			.with(jacksonModule)
			.with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);
		SchemaGeneratorConfig config = configBuilder.build();
		SchemaGenerator generator = new SchemaGenerator(config);
		JsonNode jsonNode = generator.generateSchema(this.type);
		ObjectWriter objectWriter = this.objectMapper.writer(new DefaultPrettyPrinter()
			.withObjectIndenter(new DefaultIndenter().withLinefeed(System.lineSeparator())));
		try {
			this.jsonSchema = objectWriter.writeValueAsString(jsonNode);
		}
		catch (JsonProcessingException e) {
			logger.error("Could not pretty print json schema for jsonNode: " + jsonNode);
			throw new RuntimeException("Could not pretty print json schema for " + this.type, e);
		}
	}

	/**
	 * Parses the given text to transform it to the desired target type.
	 * @param text The LLM output in string format.
	 * @return The parsed output in the desired target type.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T convert(@NonNull String text) {
		try {
			// Remove leading and trailing whitespace
			text = text.trim();

			// Check for and remove triple backticks and "json" identifier
			if (text.startsWith("```") && text.endsWith("```")) {
				// Remove the first line if it contains "```json"
				String[] lines = text.split("\n", 2);
				if (lines[0].trim().equalsIgnoreCase("```json")) {
					text = lines.length > 1 ? lines[1] : "";
				}
				else {
					text = text.substring(3); // Remove leading ```
				}

				// Remove trailing ```
				text = text.substring(0, text.length() - 3);

				// Trim again to remove any potential whitespace
				text = text.trim();
			}
			return (T) this.objectMapper.readValue(text, this.objectMapper.constructType(this.type));
		}
		catch (JsonProcessingException e) {
			logger.error("Could not parse the given text to the desired target type:" + text + " into " + this.type);
			throw new RuntimeException(e);
		}
	}

	/**
	 * Configures and returns an object mapper for JSON operations.
	 * @return Configured object mapper.
	 */
	protected ObjectMapper getObjectMapper() {
		return JsonMapper.builder()
			.addModules(JacksonUtils.instantiateAvailableModules())
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
			.build();
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

	/**
	 * Provides the generated JSON schema for the target type.
	 * @return The generated JSON schema.
	 */
	public String getJsonSchema() {
		return this.jsonSchema;
	}

}

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
import java.util.Map;
import java.util.Objects;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.model.KotlinModule;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ParameterizedTypeReference;

import static org.springframework.ai.util.LoggingMarkers.SENSITIVE_DATA_MARKER;

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
 * @author Thomas Vitale
 * @author liugddx
 */
public class BeanOutputConverter<T> implements StructuredOutputConverter<T> {

	private final Logger logger = LoggerFactory.getLogger(BeanOutputConverter.class);

	/**
	 * The target class type reference to which the output will be converted.
	 */
	private final Type type;

	/** The JSON mapper used for deserialization and other JSON operations. */
	private final JsonMapper jsonMapper;

	/** Holds the generated JSON schema for the target type. */
	private String jsonSchema;

	/** The text cleaner used to preprocess LLM responses before parsing. */
	private final ResponseTextCleaner textCleaner;

	/**
	 * Constructor to initialize with the target type's class.
	 * @param clazz The target type's class.
	 */
	public BeanOutputConverter(Class<T> clazz) {
		this(clazz, null, null);
	}

	/**
	 * Constructor to initialize with the target type's class, a custom JSON mapper, and a
	 * line endings normalizer to ensure consistent line endings on any platform.
	 * @param clazz The target type's class.
	 * @param jsonMapper Custom JSON mapper for JSON operations. endings.
	 */
	public BeanOutputConverter(Class<T> clazz, @Nullable JsonMapper jsonMapper) {
		this(clazz, jsonMapper, null);
	}

	/**
	 * Constructor to initialize with the target type's class, a custom JSON mapper, and a
	 * custom text cleaner.
	 * @param clazz The target type's class.
	 * @param jsonMapper Custom JSON mapper for JSON operations.
	 * @param textCleaner Custom text cleaner for preprocessing responses.
	 */
	public BeanOutputConverter(Class<T> clazz, @Nullable JsonMapper jsonMapper,
			@Nullable ResponseTextCleaner textCleaner) {
		this(ParameterizedTypeReference.forType(clazz), jsonMapper, textCleaner);
	}

	/**
	 * Constructor to initialize with the target class type reference.
	 * @param typeRef The target class type reference.
	 */
	public BeanOutputConverter(ParameterizedTypeReference<T> typeRef) {
		this(typeRef, null, null);
	}

	/**
	 * Constructor to initialize with the target class type reference, a custom JSON
	 * mapper, and a line endings normalizer to ensure consistent line endings on any
	 * platform.
	 * @param typeRef The target class type reference.
	 * @param jsonMapper Custom JSON mapper for JSON operations. endings.
	 */
	public BeanOutputConverter(ParameterizedTypeReference<T> typeRef, @Nullable JsonMapper jsonMapper) {
		this(typeRef, jsonMapper, null);
	}

	/**
	 * Constructor to initialize with the target class type reference, a custom JSON
	 * mapper, and a custom text cleaner.
	 * @param typeRef The target class type reference.
	 * @param jsonMapper Custom JSON mapper for JSON operations.
	 * @param textCleaner Custom text cleaner for preprocessing responses.
	 */
	public BeanOutputConverter(ParameterizedTypeReference<T> typeRef, @Nullable JsonMapper jsonMapper,
			@Nullable ResponseTextCleaner textCleaner) {
		this(typeRef.getType(), jsonMapper, textCleaner);
	}

	/**
	 * Constructor to initialize with the target class type reference, a custom JSON
	 * mapper, and a line endings normalizer to ensure consistent line endings on any
	 * platform.
	 * @param type The target class type.
	 * @param jsonMapper Custom JSON mapper for JSON operations. endings.
	 * @param textCleaner Custom text cleaner for preprocessing responses.
	 */
	private BeanOutputConverter(Type type, @Nullable JsonMapper jsonMapper, @Nullable ResponseTextCleaner textCleaner) {
		Objects.requireNonNull(type, "Type cannot be null;");
		this.type = type;
		this.jsonMapper = jsonMapper != null ? jsonMapper : getJsonMapper();
		this.textCleaner = textCleaner != null ? textCleaner : createDefaultTextCleaner();
		generateSchema();
	}

	/**
	 * Creates the default text cleaner that handles common response formats from various
	 * AI models.
	 * <p>
	 * The default cleaner includes:
	 * <ul>
	 * <li>{@link ThinkingTagCleaner} - Removes thinking tags from models like Amazon Nova
	 * and Qwen. For models that don't generate thinking tags, this has minimal
	 * performance impact due to fast-path optimization.</li>
	 * <li>{@link MarkdownCodeBlockCleaner} - Removes markdown code block formatting.</li>
	 * <li>{@link WhitespaceCleaner} - Trims whitespace.</li>
	 * </ul>
	 * <p>
	 * To customize the cleaning behavior, provide a custom {@link ResponseTextCleaner}
	 * via the constructor.
	 * @return a composite text cleaner with default cleaning strategies
	 */
	private static ResponseTextCleaner createDefaultTextCleaner() {
		return CompositeResponseTextCleaner.builder()
			.addCleaner(new WhitespaceCleaner())
			.addCleaner(new ThinkingTagCleaner())
			.addCleaner(new MarkdownCodeBlockCleaner())
			.addCleaner(new WhitespaceCleaner()) // Final trim after all cleanups
			.build();
	}

	/**
	 * Generates the JSON schema for the target type.
	 */
	private void generateSchema() {
		JacksonSchemaModule jacksonModule = new JacksonSchemaModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
				JacksonOption.RESPECT_JSONPROPERTY_ORDER);
		SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
				com.github.victools.jsonschema.generator.SchemaVersion.DRAFT_2020_12,
				com.github.victools.jsonschema.generator.OptionPreset.PLAIN_JSON)
			.with(jacksonModule)
			.with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);

		configBuilder.forFields().withRequiredCheck(f -> true);

		if (KotlinDetector.isKotlinReflectPresent()) {
			configBuilder.with(new KotlinModule());
		}

		SchemaGeneratorConfig config = configBuilder.build();
		SchemaGenerator generator = new SchemaGenerator(config);
		JsonNode jsonNode = generator.generateSchema(this.type);
		postProcessSchema(jsonNode);
		ObjectWriter objectWriter = this.jsonMapper.writer()
			.with(new DefaultPrettyPrinter()
				.withObjectIndenter(new DefaultIndenter().withLinefeed(System.lineSeparator())));
		try {
			this.jsonSchema = objectWriter.writeValueAsString(jsonNode);
		}
		catch (JacksonException e) {
			logger.error("Could not pretty print json schema for jsonNode: {}", jsonNode);
			throw new RuntimeException("Could not pretty print json schema for " + this.type, e);
		}
	}

	/**
	 * Empty template method that allows for customization of the JSON schema in
	 * subclasses.
	 * @param jsonNode the JSON schema, in the form of a JSON node
	 */
	protected void postProcessSchema(@NonNull JsonNode jsonNode) {
	}

	/**
	 * Parses the given text to transform it to the desired target type.
	 * @param text The LLM output in string format.
	 * @return The parsed output in the desired target type.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T convert(String text) {
		try {
			// Clean the text using the configured text cleaner
			text = this.textCleaner.clean(text);

			return (T) this.jsonMapper.readValue(text, this.jsonMapper.constructType(this.type));
		}
		catch (JacksonException e) {
			logger.error(SENSITIVE_DATA_MARKER,
					"Could not parse the given text to the desired target type: \"{}\" into {}", text, this.type);
			throw e;
		}
	}

	/**
	 * Configures and returns a JSON mapper for JSON operations.
	 * @return Configured JSON mapper.
	 */
	protected JsonMapper getJsonMapper() {
		return JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();
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

	public Map<String, Object> getJsonSchemaMap() {
		try {
			return this.jsonMapper.readValue(this.jsonSchema, Map.class);
		}
		catch (JacksonException ex) {
			logger.error("Could not parse the JSON Schema to a Map object", ex);
			throw new IllegalStateException(ex);
		}
	}

}

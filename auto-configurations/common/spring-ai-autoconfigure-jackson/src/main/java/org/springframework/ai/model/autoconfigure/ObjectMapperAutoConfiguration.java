/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.autoconfigure;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Spring AI ObjectMapper beans. Provides
 * customizable ObjectMapper beans for JSON parsing and model options handling. Users can
 * override these beans to customize JSON processing behavior.
 *
 * @author Daniel Albuquerque
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass({ JsonParser.class, ObjectMapper.class })
public class ObjectMapperAutoConfiguration {

	/**
	 * Creates an ObjectMapper bean configured for lenient JSON parsing of LLM responses.
	 * This ObjectMapper is used by {@link JsonParser} for tool calling and structured
	 * output.
	 *
	 * <p>
	 * Default configuration:
	 * <ul>
	 * <li>Allows unescaped control characters (common in LLM responses)</li>
	 * <li>Ignores unknown properties</li>
	 * <li>Serializes dates as ISO strings instead of timestamps</li>
	 * <li>Allows empty beans to be serialized</li>
	 * </ul>
	 *
	 * <p>
	 * Users can override this bean by defining their own bean with the name
	 * "jsonParserObjectMapper".
	 * @return configured ObjectMapper for JSON parsing
	 */
	@Bean(name = "jsonParserObjectMapper", defaultCandidate = false)
	@ConditionalOnMissingBean(name = "jsonParserObjectMapper")
	public ObjectMapper jsonParserObjectMapper() {
		ObjectMapper mapper = JsonMapper.builder()
			.enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.addModules(JacksonUtils.instantiateAvailableModules())
			.build();

		// Set this ObjectMapper to be used by JsonParser
		JsonParser.setObjectMapper(mapper);

		return mapper;
	}

	/**
	 * Creates an ObjectMapper bean configured for model options
	 * serialization/deserialization. This ObjectMapper is used by
	 * {@link ModelOptionsUtils} for converting model options.
	 *
	 * <p>
	 * Default configuration:
	 * <ul>
	 * <li>Ignores unknown properties</li>
	 * <li>Allows empty beans to be serialized</li>
	 * <li>Accepts empty strings as null objects</li>
	 * <li>Coerces empty strings to null for Enum types</li>
	 * </ul>
	 *
	 * <p>
	 * Users can override this bean by defining their own bean with the name
	 * "modelOptionsObjectMapper".
	 * @return configured ObjectMapper for model options
	 */
	@Bean(name = "modelOptionsObjectMapper", defaultCandidate = false)
	@ConditionalOnMissingBean(name = "modelOptionsObjectMapper")
	public ObjectMapper modelOptionsObjectMapper() {
		ObjectMapper mapper = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.addModules(JacksonUtils.instantiateAvailableModules())
			.build()
			.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

		// Configure coercion for empty strings to null for Enum types
		// This fixes the issue where empty string finish_reason values cause
		// deserialization failures
		mapper.coercionConfigFor(Enum.class).setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);

		// Set this ObjectMapper to be used by ModelOptionsUtils
		ModelOptionsUtils.setObjectMapper(mapper);

		return mapper;
	}

}

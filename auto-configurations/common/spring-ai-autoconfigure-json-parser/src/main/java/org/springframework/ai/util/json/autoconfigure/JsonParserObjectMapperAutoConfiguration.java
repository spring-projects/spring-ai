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

package org.springframework.ai.util.json.autoconfigure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for JsonParser and ModelOptionsUtils ObjectMapper.
 * <p>
 * Provides customizable ObjectMappers for JSON parsing operations in tool calling,
 * structured output, and model options handling. Users can override these beans to
 * customize Jackson behavior.
 *
 * @author Daniel Albuquerque
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
@EnableConfigurationProperties(JsonParserProperties.class)
public class JsonParserObjectMapperAutoConfiguration {

	/**
	 * Creates a configured ObjectMapper for JsonParser operations.
	 * <p>
	 * This ObjectMapper is configured with:
	 * <ul>
	 * <li>Lenient deserialization (doesn't fail on unknown properties)</li>
	 * <li>Proper handling of empty beans during serialization</li>
	 * <li>Standard Jackson modules (Java 8, JSR-310, ParameterNames, Kotlin)</li>
	 * <li>Optional features from JsonParserProperties</li>
	 * </ul>
	 *
	 * To customize, provide your own bean: <pre>{@code
	 * &#64;Bean
	 * public ObjectMapper jsonParserObjectMapper() {
	 *     return JsonMapper.builder()
	 *         .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
	 *         .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
	 *         .build();
	 * }
	 * }</pre>
	 * @param properties the JsonParser configuration properties
	 * @return the configured ObjectMapper
	 */
	@Bean(name = "jsonParserObjectMapper", defaultCandidate = false)
	@ConditionalOnMissingBean(name = "jsonParserObjectMapper")
	public ObjectMapper jsonParserObjectMapper(JsonParserProperties properties) {
		JsonMapper.Builder builder = JsonMapper.builder()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.addModules(JacksonUtils.instantiateAvailableModules());

		// Apply properties
		if (properties.isAllowUnescapedControlChars()) {
			builder.enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS);
		}

		if (!properties.isWriteDatesAsTimestamps()) {
			builder.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		}

		return builder.build();
	}

	/**
	 * Creates a configured ObjectMapper for ModelOptionsUtils operations.
	 * <p>
	 * This ObjectMapper is configured with:
	 * <ul>
	 * <li>Lenient deserialization (doesn't fail on unknown properties)</li>
	 * <li>Proper handling of empty beans during serialization</li>
	 * <li>Standard Jackson modules (Java 8, JSR-310, ParameterNames, Kotlin)</li>
	 * <li>Empty string to null coercion for objects and enums</li>
	 * <li>Optional features from JsonParserProperties</li>
	 * </ul>
	 *
	 * To customize, provide your own bean: <pre>{@code
	 * &#64;Bean
	 * public ObjectMapper modelOptionsObjectMapper() {
	 *     ObjectMapper mapper = JsonMapper.builder()
	 *         .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
	 *         .addModules(JacksonUtils.instantiateAvailableModules())
	 *         .build()
	 *         .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
	 *
	 *     mapper.coercionConfigFor(Enum.class)
	 *         .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
	 *
	 *     return mapper;
	 * }
	 * }</pre>
	 * @param properties the configuration properties
	 * @return the configured ObjectMapper
	 */
	@Bean(name = "modelOptionsObjectMapper", defaultCandidate = false)
	@ConditionalOnMissingBean(name = "modelOptionsObjectMapper")
	public ObjectMapper modelOptionsObjectMapper(JsonParserProperties properties) {
		JsonMapper.Builder builder = JsonMapper.builder();

		// Apply base configuration
		if (properties.isFailOnUnknownProperties()) {
			builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		}
		else {
			builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		}

		if (properties.isFailOnEmptyBeans()) {
			builder.enable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		}
		else {
			builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		}

		builder.addModules(JacksonUtils.instantiateAvailableModules());

		ObjectMapper mapper = builder.build();

		// Configure empty string handling
		if (properties.isAcceptEmptyStringAsNull()) {
			mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
		}

		// Configure enum coercion (critical for API compatibility)
		if (properties.isCoerceEmptyEnumStrings()) {
			mapper.coercionConfigFor(Enum.class).setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
		}

		return mapper;
	}

	/**
	 * Creates a JsonParser bean configured with the custom ObjectMapper. This also sets
	 * the static configured mapper for backward compatibility with code using static
	 * methods.
	 * @param objectMapper the configured ObjectMapper
	 * @return the JsonParser instance
	 */
	@Bean
	@ConditionalOnMissingBean
	public JsonParser jsonParser(@Qualifier("jsonParserObjectMapper") ObjectMapper objectMapper) {
		// Set the static mapper for backward compatibility
		JsonParser.setConfiguredObjectMapper(objectMapper);

		// Create bean instance
		return new JsonParser(objectMapper);
	}

	/**
	 * Initializes ModelOptionsUtils with the Spring-managed ObjectMapper. This setter
	 * allows ModelOptionsUtils static methods to use the Spring-configured mapper while
	 * maintaining backward compatibility.
	 * @param objectMapper the configured ObjectMapper for model options
	 */
	@Bean
	@ConditionalOnMissingBean(name = "modelOptionsUtilsInitializer")
	public Object modelOptionsUtilsInitializer(@Qualifier("modelOptionsObjectMapper") ObjectMapper objectMapper) {
		// Set the static mapper for backward compatibility
		ModelOptionsUtils.setConfiguredObjectMapper(objectMapper);

		// Return a marker object to satisfy bean contract
		return new Object();
	}

}

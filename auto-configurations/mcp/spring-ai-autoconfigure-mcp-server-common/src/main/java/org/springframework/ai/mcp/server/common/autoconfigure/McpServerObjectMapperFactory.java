/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.server.common.autoconfigure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.springframework.ai.util.JacksonUtils;

/**
 * Factory class for creating properly configured {@link ObjectMapper} instances for MCP
 * server operations.
 * <p>
 * This factory ensures consistent JSON serialization/deserialization configuration across
 * all MCP server transport types (STDIO, SSE, Streamable-HTTP, Stateless). The
 * configuration is optimized for MCP protocol compliance and handles common edge cases
 * that can cause serialization failures.
 * <p>
 * Key configuration features:
 * <ul>
 * <li><b>Lenient Deserialization:</b> Does not fail on unknown JSON properties, allowing
 * forward compatibility</li>
 * <li><b>Empty Bean Handling:</b> Does not fail when serializing beans without
 * properties</li>
 * <li><b>Null Value Exclusion:</b> Excludes null values from JSON output for cleaner
 * messages</li>
 * <li><b>Date/Time Formatting:</b> Uses ISO-8601 format instead of timestamps</li>
 * <li><b>Jackson Modules:</b> Registers standard modules for Java 8, JSR-310, parameter
 * names, and Kotlin (if available)</li>
 * </ul>
 *
 * @author Spring AI Team
 */
public final class McpServerObjectMapperFactory {

	private McpServerObjectMapperFactory() {
		// Utility class - prevent instantiation
	}

	/**
	 * Creates a new {@link ObjectMapper} instance configured for MCP server operations.
	 * <p>
	 * This method creates a fresh ObjectMapper with standard configuration suitable for
	 * MCP protocol serialization/deserialization. Each call creates a new instance, so
	 * callers may want to cache the result if creating multiple instances.
	 * @return a properly configured ObjectMapper instance
	 */
	public static ObjectMapper createObjectMapper() {
		return JsonMapper.builder()
			// Deserialization configuration
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
			// Serialization configuration
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
			.serializationInclusion(JsonInclude.Include.NON_NULL)
			// Register standard Jackson modules (Jdk8, JavaTime, ParameterNames, Kotlin)
			.addModules(JacksonUtils.instantiateAvailableModules())
			.build();
	}

	/**
	 * Retrieves an ObjectMapper from the provided provider, or creates a configured
	 * default if none is available.
	 * <p>
	 * This method is designed for use in Spring auto-configuration classes where an
	 * ObjectMapper may optionally be provided by the user. If no ObjectMapper bean is
	 * available, this method ensures a properly configured instance is used rather than a
	 * vanilla ObjectMapper.
	 * <p>
	 * Example usage in auto-configuration:
	 *
	 * <pre>{@code
	 * &#64;Bean
	 * public TransportProvider transport(ObjectProvider<ObjectMapper> objectMapperProvider) {
	 *     ObjectMapper mapper = McpServerObjectMapperFactory.getOrCreateObjectMapper(objectMapperProvider);
	 *     return new TransportProvider(mapper);
	 * }
	 * }</pre>
	 * @param objectMapperProvider the Spring ObjectProvider for ObjectMapper beans
	 * @return the provided ObjectMapper, or a newly configured default instance
	 */
	public static ObjectMapper getOrCreateObjectMapper(
			org.springframework.beans.factory.ObjectProvider<ObjectMapper> objectMapperProvider) {
		return objectMapperProvider.getIfAvailable(McpServerObjectMapperFactory::createObjectMapper);
	}

}

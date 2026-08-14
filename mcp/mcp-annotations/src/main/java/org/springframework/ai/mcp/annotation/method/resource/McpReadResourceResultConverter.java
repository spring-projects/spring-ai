/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.mcp.annotation.method.resource;

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;

import org.springframework.ai.mcp.annotation.method.resource.AbstractMcpResourceMethodCallback.ContentType;

/**
 * Interface for converting method return values to {@link ReadResourceResult}.
 * <p>
 * This interface defines a contract for converting various return types from resource
 * methods to a standardized {@link ReadResourceResult} format.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public interface McpReadResourceResultConverter {

	/**
	 * Converts the method's return value to a {@link ReadResourceResult}.
	 * <p>
	 * This method handles various return types and converts them to a standardized
	 * {@link ReadResourceResult} format.
	 * @param result The method's return value
	 * @param requestUri The original request URI
	 * @param mimeType The MIME type of the resource
	 * @param contentType The content type of the resource
	 * @return A {@link ReadResourceResult} containing the appropriate resource contents
	 * @throws IllegalArgumentException if the return type is not supported
	 */
	ReadResourceResult convertToReadResourceResult(Object result, String requestUri, String mimeType,
			ContentType contentType);

	/**
	 * Converts the method's return value to a {@link ReadResourceResult}, propagating
	 * resource-level metadata to the content items.
	 * <p>
	 * This default method delegates to the original
	 * {@link #convertToReadResourceResult(Object, String, String, ContentType)} to ensure
	 * backwards compatibility with existing custom implementations.
	 * @param result The method's return value
	 * @param requestUri The original request URI
	 * @param mimeType The MIME type of the resource
	 * @param contentType The content type of the resource
	 * @param meta The resource-level metadata to propagate to content items
	 * @return A {@link ReadResourceResult} containing the appropriate resource contents
	 * @throws IllegalArgumentException if the return type is not supported
	 */
	default ReadResourceResult convertToReadResourceResult(Object result, String requestUri, String mimeType,
			ContentType contentType, Map<String, Object> meta) {
		return convertToReadResourceResult(result, requestUri, mimeType, contentType);
	}

}

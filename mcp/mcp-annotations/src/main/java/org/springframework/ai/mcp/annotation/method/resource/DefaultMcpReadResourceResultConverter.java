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

package org.springframework.ai.mcp.annotation.method.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;

import org.springframework.ai.mcp.annotation.method.resource.AbstractMcpResourceMethodCallback.ContentType;

/**
 * Default implementation of {@link McpReadResourceResultConverter}.
 * <p>
 * This class provides a standard implementation for converting various return types from
 * resource methods to a standardized {@link ReadResourceResult} format.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public class DefaultMcpReadResourceResultConverter implements McpReadResourceResultConverter {

	/**
	 * Default MIME type to use when none is specified.
	 */
	private static final String DEFAULT_MIME_TYPE = "text/plain";

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
	@Override
	public ReadResourceResult convertToReadResourceResult(Object result, String requestUri, String mimeType,
			ContentType contentType) {
		return convertToReadResourceResult(result, requestUri, mimeType, contentType, null);
	}

	/**
	 * Converts the method's return value to a {@link ReadResourceResult}, propagating
	 * resource-level metadata to the content items.
	 * @param result The method's return value
	 * @param requestUri The original request URI
	 * @param mimeType The MIME type of the resource
	 * @param contentType The content type of the resource
	 * @param meta The resource-level metadata to propagate to content items
	 * @return A {@link ReadResourceResult} containing the appropriate resource contents
	 * @throws IllegalArgumentException if the return type is not supported
	 */
	@Override
	public ReadResourceResult convertToReadResourceResult(Object result, String requestUri, String mimeType,
			ContentType contentType, Map<String, Object> meta) {
		if (result == null) {
			return new ReadResourceResult(List.of());
		}

		if (result instanceof ReadResourceResult) {
			return (ReadResourceResult) result;
		}

		mimeType = (mimeType != null && !mimeType.isEmpty()) ? mimeType : DEFAULT_MIME_TYPE;

		// Determine content type from mime type since contentType() was moved from
		// McpResource
		contentType = contentType != null ? contentType
				: isTextMimeType(mimeType) ? ContentType.TEXT : ContentType.BLOB;

		List<ResourceContents> contents;

		if (result instanceof List<?>) {
			contents = convertListResult((List<?>) result, requestUri, contentType, mimeType, meta);
		}
		else if (result instanceof ResourceContents) {
			// Single ResourceContents
			contents = List.of((ResourceContents) result);
		}
		else if (result instanceof String) {
			// Single String -> ResourceContents (TextResourceContents or
			// BlobResourceContents)
			contents = convertStringResult((String) result, requestUri, contentType, mimeType, meta);
		}
		else {
			throw new IllegalArgumentException("Unsupported return type: " + result.getClass().getName());
		}

		return new ReadResourceResult(contents);
	}

	private boolean isTextMimeType(String mimeType) {
		if (mimeType == null) {
			return false;
		}

		// Direct text types
		if (mimeType.startsWith("text/")) {
			return true;
		}

		// Common text-based MIME types that don't start with "text/"
		return mimeType.equals("application/json") || mimeType.equals("application/xml")
				|| mimeType.equals("application/javascript") || mimeType.equals("application/ecmascript")
				|| mimeType.equals("application/x-httpd-php") || mimeType.equals("application/xhtml+xml")
				|| mimeType.endsWith("+json") || mimeType.endsWith("+xml");
	}

	/**
	 * Converts a List result to a list of ResourceContents with metadata.
	 * @param list The list result
	 * @param requestUri The original request URI
	 * @param contentType The content type (TEXT or BLOB)
	 * @param mimeType The MIME type
	 * @param meta The resource-level metadata to propagate to content items
	 * @return A list of ResourceContents
	 * @throws IllegalArgumentException if the list item type is not supported
	 */
	@SuppressWarnings("unchecked")
	private List<ResourceContents> convertListResult(List<?> list, String requestUri, ContentType contentType,
			String mimeType, Map<String, Object> meta) {
		if (list.isEmpty()) {
			return List.of();
		}

		Object firstItem = list.get(0);

		if (firstItem instanceof ResourceContents) {
			// List<ResourceContents>
			return (List<ResourceContents>) list;
		}
		else if (firstItem instanceof String) {
			// List<String> -> List<ResourceContents> (TextResourceContents or
			// BlobResourceContents)
			List<String> stringList = (List<String>) list;
			List<ResourceContents> result = new ArrayList<>(stringList.size());

			if (contentType == ContentType.TEXT) {
				for (String text : stringList) {
					result.add(new TextResourceContents(requestUri, mimeType, text, meta));
				}
			}
			else { // BLOB
				for (String blob : stringList) {
					result.add(new BlobResourceContents(requestUri, mimeType, blob, meta));
				}
			}

			return result;
		}
		else {
			throw new IllegalArgumentException("Unsupported list item type: " + firstItem.getClass().getName()
					+ ". Expected String or ResourceContents.");
		}
	}

	/**
	 * Converts a String result to a list of ResourceContents with metadata.
	 * @param stringResult The string result
	 * @param requestUri The original request URI
	 * @param contentType The content type (TEXT or BLOB)
	 * @param mimeType The MIME type
	 * @param meta The resource-level metadata to propagate to content items
	 * @return A list containing a single ResourceContents
	 */
	private List<ResourceContents> convertStringResult(String stringResult, String requestUri, ContentType contentType,
			String mimeType, Map<String, Object> meta) {
		if (contentType == ContentType.TEXT) {
			return List.of(new TextResourceContents(requestUri, mimeType, stringResult, meta));
		}
		else { // BLOB
			return List.of(new BlobResourceContents(requestUri, mimeType, stringResult, meta));
		}
	}

}

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

package org.springframework.ai.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.DefaultResourceLoader;

/**
 * Miscellaneous Resource utility methods. Mainly for use within Spring AI
 *
 * @author Christian Tzolov
 */
public abstract class ResourceUtils {

	/**
	 * Retrieves the content of a resource as a UTF-8 encoded string.
	 *
	 * This method uses Spring's DefaultResourceLoader to load the resource from the given
	 * URI and then reads its content as a string using UTF-8 encoding. If an IOException
	 * occurs during reading, it is wrapped in a RuntimeException.
	 * @param uri The URI of the resource to be read. This can be any URI supported by
	 * Spring's ResourceLoader, such as "classpath:", "file:", or "http:".
	 * @return The content of the resource as a string.
	 * @throws RuntimeException If an error occurs while reading the resource. This
	 * exception wraps the original IOException.
	 */
	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}

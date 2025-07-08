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

package org.springframework.ai.chat.messages;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

/**
 * Utility class for managing messages.
 *
 * @author Thomas Vitale
 */
final class MessageUtils {

	private MessageUtils() {
	}

	static String readResource(Resource resource) {
		return readResource(resource, Charset.defaultCharset());
	}

	static String readResource(Resource resource, Charset charset) {
		Assert.notNull(resource, "resource cannot be null");
		Assert.notNull(charset, "charset cannot be null");
		try (InputStream inputStream = resource.getInputStream()) {
			return StreamUtils.copyToString(inputStream, charset);
		}
		catch (IOException ex) {
			throw new RuntimeException("Failed to read resource", ex);
		}
	}

}

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

package org.springframework.ai.model;

import java.io.IOException;
import java.net.URL;

import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * The Media class represents the data and metadata of a media attachment in a message. It
 * consists of a MIME type and the raw data.
 *
 * This class is used as a parameter in the constructor of the UserMessage class.
 *
 * @author Christian Tzolov
 * @since 0.8.1
 */
public class Media {

	private final MimeType mimeType;

	private final Object data;

	public Media(MimeType mimeType, URL url) {
		Assert.notNull(mimeType, "MimeType must not be null");
		this.mimeType = mimeType;
		this.data = url.toString();
	}

	public Media(MimeType mimeType, Resource resource) {
		Assert.notNull(mimeType, "MimeType must not be null");
		this.mimeType = mimeType;
		try {
			this.data = resource.getContentAsByteArray();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public MimeType getMimeType() {
		return this.mimeType;
	}

	/**
	 * Get the media data object
	 * @return a java.net.URL.toString() or a byte[]
	 */
	public Object getData() {
		return this.data;
	}

}

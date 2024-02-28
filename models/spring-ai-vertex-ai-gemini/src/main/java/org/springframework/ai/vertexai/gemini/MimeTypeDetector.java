/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.vertexai.gemini;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Gemini supports the following MIME types:
 *
 * <ul>
 * <li>image/gif
 * <li>image/png
 * <li>image/jpeg
 * <li>video/mov
 * <li>video/mpeg
 * <li>video/mp4
 * <li>video/mpg
 * <li>video/avi
 * <li>video/wmv
 * <li>video/mpegps
 * <li>video/flv
 * </ul>
 *
 * https://cloud.google.com/vertex-ai/docs/generative-ai/model-reference/gemini
 *
 * @author Christian Tzolov
 * @since 0.8.1
 */
public abstract class MimeTypeDetector {

	/**
	 * List of all MIME types supported by the Vertex Gemini API.
	 */
	private static final Map<String, MimeType> GEMINI_MIME_TYPES = new HashMap<>();

	static {
		// Custom MIME type mappings here
		GEMINI_MIME_TYPES.put("png", MimeTypeUtils.IMAGE_PNG);
		GEMINI_MIME_TYPES.put("jpeg", MimeTypeUtils.IMAGE_JPEG);
		GEMINI_MIME_TYPES.put("jpg", MimeTypeUtils.IMAGE_JPEG);
		GEMINI_MIME_TYPES.put("gif", MimeTypeUtils.IMAGE_GIF);
		GEMINI_MIME_TYPES.put("mov", new MimeType("video", "mov"));
		GEMINI_MIME_TYPES.put("mp4", new MimeType("video", "mp4"));
		GEMINI_MIME_TYPES.put("mpg", new MimeType("video", "mpg"));
		GEMINI_MIME_TYPES.put("avi", new MimeType("video", "avi"));
		GEMINI_MIME_TYPES.put("wmv", new MimeType("video", "wmv"));
		GEMINI_MIME_TYPES.put("mpegps", new MimeType("mpegps", "mp4"));
		GEMINI_MIME_TYPES.put("flv", new MimeType("video", "flv"));
	}

	public static MimeType getMimeType(URL url) {
		return getMimeType(url.getFile());
	}

	public static MimeType getMimeType(URI uri) {
		return getMimeType(uri.toString());
	}

	public static MimeType getMimeType(File file) {
		return getMimeType(file.getAbsolutePath());
	}

	public static MimeType getMimeType(Path path) {
		return getMimeType(path.getFileName());
	}

	public static MimeType getMimeType(Resource resource) {
		try {
			return getMimeType(resource.getURI());
		}
		catch (IOException e) {
			throw new IllegalArgumentException(
					String.format("Unable to detect the MIME type of '%s'. Please provide it explicitly.",
							resource.getFilename()),
					e);
		}
	}

	public static MimeType getMimeType(String path) {

		int dotIndex = path.lastIndexOf('.');

		if (dotIndex != -1 && dotIndex < path.length() - 1) {
			String extension = path.substring(dotIndex + 1);
			MimeType customMimeType = GEMINI_MIME_TYPES.get(extension);
			if (customMimeType != null) {
				return customMimeType;
			}
		}

		throw new IllegalArgumentException(
				String.format("Unable to detect the MIME type of '%s'. Please provide it explicitly.", path));
	}

}

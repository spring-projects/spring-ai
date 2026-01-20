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

package org.springframework.ai.google.genai.api.dto;

import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * @author Danil Temnikov
 */
public class GeminiContent {

	public final List<GeminiPart> parts;

	public GeminiContent(@Nullable List<GeminiPart> parts) {
		this.parts = parts != null ? parts : Collections.emptyList();
	}

	public List<GeminiPart> getParts() {
		return parts;
	}

	/**
	 * Base sealed type for Gemini parts: text or inlineData.
	 */
	public sealed interface GeminiPart permits GeminiPartText, GeminiPartInlineData {

	}

	public static final class GeminiPartText implements GeminiPart {

		public final String text;

		public GeminiPartText(String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}

	}

	public static final class GeminiPartInlineData implements GeminiPart {

		public final InlineData inlineData;

		public GeminiPartInlineData(InlineData inlineData) {
			this.inlineData = inlineData;
		}

		public InlineData getInlineData() {
			return inlineData;
		}

		public static final class InlineData {

			/**
			 * Image MIME type, e.g. "image/png", "image/jpeg".
			 */
			public final MimeType mimeType;

			/**
			 * Base64-encoded image bytes.
			 */
			public final String data;

			public InlineData(String mimeType, String data) {
				this.mimeType = MimeTypeUtils.parseMimeType(mimeType);
				this.data = data;
			}

			public InlineData(MimeType mimeType, String data) {
				this.mimeType = mimeType;
				this.data = data;
			}

			public MimeType getMimeType() {
				return mimeType;
			}

			public String getData() {
				return data;
			}

		}

	}

}

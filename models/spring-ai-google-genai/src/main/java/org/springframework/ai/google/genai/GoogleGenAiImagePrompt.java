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
package org.springframework.ai.google.genai;

import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * GoogleGenAi-specific ImagePrompt that supports multimodal input (text + one or more
 * input images) for Gemini image generation and editing.
 *
 * <p>
 * Gemini image generation allows providing images as inline binary data together with
 * text instructions ("text-and-image-to-image"). Input images are represented as
 * {@code inlineData} parts with {@code mimeType} and base64-encoded {@code data}.
 *
 * <p>
 * This class stores structured prompt parts and does not perform serialization or
 * validation.
 *
 * @author Danil Temnikov
 */
public class GoogleGenAiImagePrompt extends ImagePrompt {

	/**
	 * Ordered multimodal parts to be mapped to Gemini {@code contents.parts}.
	 */
	private final List<InputPart> parts = new ArrayList<>();

	/**
	 * Creates a text-only image prompt.
	 */
	public GoogleGenAiImagePrompt(String instructions) {
		super(instructions);
		this.parts.add(new TextPart(instructions));
	}

	/**
	 * Creates a text-only image prompt with options.
	 */
	public GoogleGenAiImagePrompt(String instructions, ImageOptions imageOptions) {
		super(instructions, imageOptions);
		this.parts.add(new TextPart(instructions));
	}

	/**
	 * Creates a prompt from ImagePrompt messages.
	 *
	 * <p>
	 * Text messages are mirrored into Gemini text parts.
	 */
	public GoogleGenAiImagePrompt(List<ImageMessage> messages, @Nullable ImageOptions imageOptions) {
		super(messages, imageOptions);
		for (ImageMessage m : messages) {
			if (m != null && m.getText() != null && !m.getText().isBlank()) {
				this.parts.add(new TextPart(m.getText()));
			}
		}
	}

	public GoogleGenAiImagePrompt(List<ImageMessage> messages) {
		this(messages, null);
	}

	public GoogleGenAiImagePrompt(ImageMessage imageMessage, @Nullable ImageOptions imageOptions) {
		super(imageMessage, imageOptions);
		if (imageMessage != null && imageMessage.getText() != null && !imageMessage.getText().isBlank()) {
			this.parts.add(new TextPart(imageMessage.getText()));
		}
	}

	/**
	 * Returns the ordered multimodal parts.
	 */
	public List<InputPart> getParts() {
		return Collections.unmodifiableList(parts);
	}

	/**
	 * Adds a text part to the prompt.
	 */
	public GoogleGenAiImagePrompt addText(String text) {
		Objects.requireNonNull(text, "text must not be null");
		this.parts.add(new TextPart(text));
		return this;
	}

	/**
	 * Adds an input image as inline binary data.
	 * @param mimeType image MIME type (recommended values in {@link ImageMimeTypes})
	 * @param bytes raw image bytes (will be base64-encoded)
	 */
	public GoogleGenAiImagePrompt addInlineImage(String mimeType, byte[] bytes) {
		Objects.requireNonNull(mimeType, "mimeType must not be null");
		Objects.requireNonNull(bytes, "bytes must not be null");
		this.parts.add(InlineImagePart.fromBytes(mimeType, bytes));
		return this;
	}

	/**
	 * Adds an input image as inline base64 data.
	 * @param mimeType image MIME type (recommended values in {@link ImageMimeTypes})
	 * @param base64 base64-encoded image bytes (without data URI prefix)
	 */
	public GoogleGenAiImagePrompt addInlineImageBase64(String mimeType, String base64) {
		Objects.requireNonNull(mimeType, "mimeType must not be null");
		Objects.requireNonNull(base64, "base64 must not be null");
		this.parts.add(InlineImagePart.fromBase64(mimeType, base64));
		return this;
	}

	/**
	 * Convenience helper: adds an image part followed by a text part.
	 */
	public GoogleGenAiImagePrompt addInlineImageThenText(String mimeType, byte[] bytes, String text) {
		return addInlineImage(mimeType, bytes).addText(text);
	}

	// ---------------------------------------------------------------------
	// Input parts
	// ---------------------------------------------------------------------

	/**
	 * Marker interface for Gemini request parts.
	 */
	public interface InputPart {

		/**
		 * @return Gemini wire-kind ("text" or "inlineData")
		 */
		String kind();

	}

	/**
	 * Text instruction part.
	 */
	public static final class TextPart implements InputPart {

		private final String text;

		public TextPart(String text) {
			this.text = Objects.requireNonNull(text, "text must not be null");
		}

		public String getText() {
			return text;
		}

		@Override
		public String kind() {
			return "text";
		}

	}

	/**
	 * Inline image part.
	 *
	 * <p>
	 * Serialized as: <pre>
	 * {
	 *   "inlineData": {
	 *     "mimeType": "...",
	 *     "data": "base64..."
	 *   }
	 * }
	 * </pre>
	 */
	public static final class InlineImagePart implements InputPart {

		private final MimeType mimeType;

		private final String base64Data;

		private InlineImagePart(MimeType mimeType, String base64Data) {
			this.mimeType = mimeType;
			this.base64Data = Objects.requireNonNull(base64Data, "base64Data must not be null");
		}

		private InlineImagePart(String mimeTypeStr, String base64Data) {
			this(MimeTypeUtils.parseMimeType(mimeTypeStr), base64Data);
		}

		public static InlineImagePart fromBytes(String mimeType, byte[] bytes) {
			String base64 = Base64.getEncoder().encodeToString(bytes);
			return new InlineImagePart(mimeType, base64);
		}

		public static InlineImagePart fromBytes(MimeType mimeType, byte[] bytes) {
			String base64 = Base64.getEncoder().encodeToString(bytes);
			return new InlineImagePart(mimeType, base64);
		}

		public static InlineImagePart fromBase64(String mimeType, String base64Data) {
			return new InlineImagePart(mimeType, base64Data);
		}

		public static InlineImagePart fromBase64(MimeType mimeType, String base64Data) {
			return new InlineImagePart(mimeType, base64Data);
		}

		public MimeType getMimeType() {
			return mimeType;
		}

		public String getBase64Data() {
			return base64Data;
		}

		@Override
		public String kind() {
			return "inlineData";
		}

	}

	// ---------------------------------------------------------------------
	// MIME type helpers (recommended, not enforced)
	// ---------------------------------------------------------------------

	/**
	 * Recommended image MIME types for Gemini image generation input.
	 *
	 * <p>
	 * These constants are provided for convenience and autocomplete only. The API may
	 * support additional image types in the future.
	 */
	public static final class ImageMimeTypes {

		/**
		 * PNG image (lossless).
		 */
		public static final String PNG = "image/png";

		/**
		 * JPEG image.
		 */
		public static final String JPEG = "image/jpeg";

		/**
		 * WEBP image.
		 */
		public static final String WEBP = "image/webp";

		private ImageMimeTypes() {
		}

	}

}

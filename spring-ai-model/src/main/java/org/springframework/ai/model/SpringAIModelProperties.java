/*
 * Copyright 2025-2026 the original author or authors.
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

public final class SpringAIModelProperties {

	private SpringAIModelProperties() {
		// Avoids instantiation
	}

	public static final String MODEL_PREFIX = "spring.ai.model";

	public static final String CHAT_MODEL = MODEL_PREFIX + ".chat";

	public static final String EMBEDDING_MODEL = MODEL_PREFIX + ".embedding";

	public static final String TEXT_EMBEDDING_MODEL = MODEL_PREFIX + ".embedding.text";

	public static final String MULTI_MODAL_EMBEDDING_MODEL = MODEL_PREFIX + ".embedding.multimodal";

	public static final String IMAGE_MODEL = MODEL_PREFIX + ".image";

	public static final String AUDIO_TRANSCRIPTION_MODEL = MODEL_PREFIX + ".audio.transcription";

	public static final String AUDIO_SPEECH_MODEL = MODEL_PREFIX + ".audio.speech";

	public static final String MODERATION_MODEL = MODEL_PREFIX + ".moderation";

	public static final String OCR_MODEL = MODEL_PREFIX + ".ocr";

}

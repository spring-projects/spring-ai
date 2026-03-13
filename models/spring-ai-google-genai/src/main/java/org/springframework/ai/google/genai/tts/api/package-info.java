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

/**
 * Low-level API client and POJOs for Google Gemini Text-to-Speech.
 *
 * <p>
 * This package contains the HTTP client implementation and request/response data
 * structures for the Gemini TTS API.
 *
 * <h2>Key Classes</h2>
 * <ul>
 * <li>{@link org.springframework.ai.google.genai.tts.api.GeminiTtsApi} - RestClient-based
 * HTTP client</li>
 * <li>{@link org.springframework.ai.google.genai.tts.api.GeminiTtsApi.GenerateContentRequest}
 * - API request</li>
 * <li>{@link org.springframework.ai.google.genai.tts.api.GeminiTtsApi.GenerateContentResponse}
 * - API response</li>
 * <li>{@link org.springframework.ai.google.genai.tts.api.GeminiTtsApi.SpeakerVoiceConfig}
 * - Multi-speaker configuration</li>
 * </ul>
 *
 * <h2>API Endpoint</h2> <pre>
 * POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
 * </pre>
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@NonNullApi
@NonNullFields
package org.springframework.ai.google.genai.tts.api;

import org.springframework.lang.NonNullApi;
import org.springframework.lang.NonNullFields;

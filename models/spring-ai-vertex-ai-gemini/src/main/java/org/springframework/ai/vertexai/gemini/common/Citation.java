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

package org.springframework.ai.vertexai.gemini.common;

import java.time.LocalDate;

import org.springframework.lang.Nullable;

/**
 * Represents a citation returned by the Vertex AI Gemini API for generated content. Each
 * citation identifies a source that was used in generating the response, with character
 * index ranges indicating which part of the response text is attributed to the source.
 *
 * @param startIndex the start index of the cited text in the response (inclusive)
 * @param endIndex the end index of the cited text in the response (exclusive)
 * @param uri the URI of the cited source
 * @param title the title of the cited source
 * @param license the license under which the cited source is available
 * @param publicationDate the publication date of the cited source, or null if unavailable
 * @author Alessio Subbaiah
 * @since 1.0.0
 */
public record Citation(int startIndex, int endIndex, String uri, String title, String license,
                       @Nullable LocalDate publicationDate) {

}

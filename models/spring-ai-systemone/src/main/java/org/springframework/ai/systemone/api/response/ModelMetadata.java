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

package org.springframework.ai.systemone.api.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * One entry of {@code GET /v1/models}.
 *
 * @param name the model id or alias to send as {@code model}
 * @param description what the model is for
 * @param releaseDate when the model was released, an ISO-8601 timestamp with offset such
 * as {@code 2026-09-10T18:38:01.391457+00:00}. Kept as a string: it is displayed far more
 * often than it is compared, and the API is free to change its precision.
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelMetadata(@JsonProperty("name") String name,
		@JsonProperty("description") @Nullable String description,
		@JsonProperty("release_date") @Nullable String releaseDate) {
}

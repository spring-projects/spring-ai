/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.watsonx.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// @formatter:off
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WatsonxAiResults(
        @JsonProperty("generated_text") String generatedText,
        @JsonProperty("generated_token_count") Integer generatedTokenCount,
        @JsonProperty("input_token_count") Integer inputTokenCount,
        @JsonProperty("stop_reason") String stopReason
) { }

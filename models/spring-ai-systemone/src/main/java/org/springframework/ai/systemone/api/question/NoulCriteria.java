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

package org.springframework.ai.systemone.api.question;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.JsonContent;

/**
 * Optional descriptions of the two sides of a {@link Noul}. Supplying them pins down a
 * subtle yes/no boundary that the instructions alone leave ambiguous.
 *
 * @param whenTrue what the yes outcome means, or {@code null} to leave it undescribed
 * @param whenFalse what the no outcome means, or {@code null} to leave it undescribed
 * @author Christian Tzolov
 * @since 2.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoulCriteria(@JsonProperty("true") @Nullable JsonContent whenTrue,
		@JsonProperty("false") @Nullable JsonContent whenFalse) {
}

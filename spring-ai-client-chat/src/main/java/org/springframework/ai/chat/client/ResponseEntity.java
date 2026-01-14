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

package org.springframework.ai.chat.client;

import org.springframework.lang.Nullable;

/**
 * Represents a {@link org.springframework.ai.model.Model} response that includes the
 * entire response along with the specified response entity type.
 *
 * @param <R> the entire response type.
 * @param <E> the converted entity type.
 * @param response the entire response object.
 * @param entity the converted entity object.
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public record ResponseEntity<R, E>(@Nullable R response, @Nullable E entity) {

	@Nullable
	public R getResponse() {
		return this.response;
	}

	@Nullable
	public E getEntity() {
		return this.entity;
	}

}

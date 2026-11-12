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

package org.springframework.ai.chat.client

import org.springframework.ai.chat.model.ChatResponse
import org.springframework.core.ParameterizedTypeReference

/**
 * Extensions for [ChatClient] providing a reified generic adapters for `entity` and `responseEntity`
 *
 * @author Josh Long
 */

/**
 * Deserializes the response into a [T] instance.
 *
 * Returns `null` when the model response is empty or contains no parsable
 * content, matching the `@Nullable` contract of the underlying
 * [ChatClient.CallResponseSpec.entity] method. The previous implementation
 * used an unchecked `as T` cast which caused an eager
 * [NullPointerException] the moment the model returned no content.
 */
inline fun <reified T : Any> ChatClient.CallResponseSpec.entity(): T? =
	entity(object : ParameterizedTypeReference<T>() {})

inline fun <reified T : Any> ChatClient.CallResponseSpec.responseEntity(): ResponseEntity<ChatResponse, T> =
	responseEntity(object : ParameterizedTypeReference<T>() {}) 

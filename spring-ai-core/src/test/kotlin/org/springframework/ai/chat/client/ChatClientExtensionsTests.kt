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

package org.springframework.ai.chat.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.core.ParameterizedTypeReference

class ChatClientExtensionsTests {

	data class Joke(val setup: String, val punchline: String)

	@Test
	fun responseEntity() {
		val crs = mockk<ChatClient.CallResponseSpec>()
		val re = mockk<ResponseEntity<ChatResponse, Joke>>()
		every { crs.responseEntity<Joke>() } returns re
		crs.responseEntity<Joke>()
		verify { crs.responseEntity(object : ParameterizedTypeReference<Joke>() {}) }
	}
	
	@Test
	fun entity() {
		val crs = mockk<ChatClient.CallResponseSpec>()
		val joke =  mockk<Joke>()
		every { crs.entity(any<ParameterizedTypeReference<Joke>>()) } returns joke 
		crs.entity<Joke>()
		verify { crs.entity(object : ParameterizedTypeReference<Joke>(){}) }
	}
}

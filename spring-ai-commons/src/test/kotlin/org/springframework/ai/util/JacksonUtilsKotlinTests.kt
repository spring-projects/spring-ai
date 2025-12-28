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
package org.springframework.ai.util

import com.fasterxml.jackson.databind.json.JsonMapper
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Kotlin unit tests for [JacksonUtils].
 *
 * @author Sebastien Deleuze
 */
class JacksonUtilsKotlinTests {

	@Test
	fun `Deserialize to a Kotlin data class with Jackson modules detected by JacksonUtils#instantiateAvailableModules`() {
		val jsonMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build()
		val output = jsonMapper.readValue("{\"name\":\"Robert\",\"age\":42}", User::class.java)
		Assertions.assertThat(output).isEqualTo(User("Robert", 42))
	}

	@Test
	fun `Serialize a Kotlin data class with Jackson modules detected by JacksonUtils#instantiateAvailableModules`() {
		val jsonMapper = JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build()
		val output = jsonMapper.writeValueAsString(User("Robert", 42))
		Assertions.assertThat(output).isEqualTo("{\"name\":\"Robert\",\"age\":42}")
	}

	data class User(val name: String, val age: Int)

}
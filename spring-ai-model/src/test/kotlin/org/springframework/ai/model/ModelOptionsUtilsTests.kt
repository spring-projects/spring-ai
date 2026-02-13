/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.lang.reflect.Type

class KotlinModelOptionsUtilsTests {

	private class Foo(val bar: String, val baz: String?)
	private class FooWithDefault(val bar: String, val baz: Int = 10)

	private val jsonMapper = JsonMapper()

	@Test
	fun `test ModelOptionsUtils with Kotlin data class`() {
		val portableOptions = Foo("John", "Doe")

		val optionsMap = ModelOptionsUtils.objectToMap(portableOptions)
		assertThat(optionsMap).containsEntry("bar", "John")
		assertThat(optionsMap).containsEntry("baz", "Doe")

		val newPortableOptions = ModelOptionsUtils.mapToClass(optionsMap, Foo::class.java)
		assertThat(newPortableOptions.bar).isEqualTo("John")
		assertThat(newPortableOptions.baz).isEqualTo("Doe")
	}

	@Test
	fun `test Kotlin data class schema generation using getJsonSchema`() {
		val inputType: Type = Foo::class.java

		val schemaJson = ModelOptionsUtils.getJsonSchema(inputType, false)

		val schemaNode = jsonMapper.readTree(schemaJson)

		val required = schemaNode["required"]
		assertThat(required).isNotNull
		assertThat(required.toString()).contains("bar")
		assertThat(required.toString()).doesNotContain("baz")

		val properties = schemaNode["properties"]
		assertThat(properties["bar"]["type"].asString()).isEqualTo("string")

		val bazTypeNode = properties["baz"]["type"]
		if (bazTypeNode.isArray) {
			assertThat(bazTypeNode.toString()).contains("string")
			assertThat(bazTypeNode.toString()).contains("null")
		} else {
			assertThat(bazTypeNode.asString()).isEqualTo("string")
		}
	}

	@Test
	fun `test data class with default values`() {
		val inputType: Type = FooWithDefault::class.java

		val schemaJson = ModelOptionsUtils.getJsonSchema(inputType, false)

		val schemaNode = jsonMapper.readTree(schemaJson)

		val required = schemaNode["required"]
		assertThat(required).isNotNull
		assertThat(required.toString()).contains("bar")
		assertThat(required.toString()).doesNotContain("baz")

		val properties = schemaNode["properties"]
		assertThat(properties["bar"]["type"].asString()).isEqualTo("string")

		val bazTypeNode = properties["baz"]["type"]
		assertThat(bazTypeNode.asString()).isEqualTo("integer")
	}
}

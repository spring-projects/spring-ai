package org.springframework.ai.model

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.Type

class KotlinModelOptionsUtilsTests {

	private class Foo(val bar: String, val baz: String?)
	private class FooWithDefault(val bar: String, val baz: Int = 10)

	private val objectMapper = ObjectMapper()

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

		val schemaNode = objectMapper.readTree(schemaJson)

		val required = schemaNode["required"]
		assertThat(required).isNotNull
		assertThat(required.toString()).contains("bar")
		assertThat(required.toString()).doesNotContain("baz")

		val properties = schemaNode["properties"]
		assertThat(properties["bar"]["type"].asText()).isEqualTo("string")

		val bazTypeNode = properties["baz"]["type"]
		if (bazTypeNode.isArray) {
			assertThat(bazTypeNode.toString()).contains("string")
			assertThat(bazTypeNode.toString()).contains("null")
		} else {
			assertThat(bazTypeNode.asText()).isEqualTo("string")
		}
	}

	@Test
	fun `test data class with default values`() {
		val inputType: Type = FooWithDefault::class.java

		val schemaJson = ModelOptionsUtils.getJsonSchema(inputType, false)

		val schemaNode = objectMapper.readTree(schemaJson)

		val required = schemaNode["required"]
		assertThat(required).isNotNull
		assertThat(required.toString()).contains("bar")
		assertThat(required.toString()).doesNotContain("baz")

		val properties = schemaNode["properties"]
		assertThat(properties["bar"]["type"].asText()).isEqualTo("string")

		val bazTypeNode = properties["baz"]["type"]
		assertThat(bazTypeNode.asText()).isEqualTo("integer")
	}
}

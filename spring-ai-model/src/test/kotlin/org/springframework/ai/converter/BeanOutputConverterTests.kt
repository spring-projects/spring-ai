package org.springframework.ai.converter

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KotlinBeanOutputConverterTests {

	private data class Foo(val bar: String, val baz: String?)
	private data class FooWithDefault(val bar: String, val baz: Int = 10)

	private val objectMapper = ObjectMapper()

	@Test
	fun `test Kotlin data class schema generation using getJsonSchema`() {
		val converter = BeanOutputConverter(Foo::class.java)

		val schemaJson = converter.jsonSchema

		val schemaNode = objectMapper.readTree(schemaJson)

		val required = schemaNode["required"]
		assertThat(required).isNotNull
		assertThat(required.toString()).contains("bar")
		assertThat(required.toString()).contains("baz")

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
	fun `test Kotlin data class with default values`() {
		val converter = BeanOutputConverter(FooWithDefault::class.java)

		val schemaJson = converter.jsonSchema

		val schemaNode = objectMapper.readTree(schemaJson)

		val required = schemaNode["required"]
		assertThat(required).isNotNull
		assertThat(required.toString()).contains("bar")
		assertThat(required.toString()).contains("baz")

		val properties = schemaNode["properties"]
		assertThat(properties["bar"]["type"].asText()).isEqualTo("string")

		val bazTypeNode = properties["baz"]["type"]
		assertThat(bazTypeNode.asText()).isEqualTo("integer")
	}
}

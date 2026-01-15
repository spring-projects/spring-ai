/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.tool.augment;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive test suite for {@link AugmentedToolCallback} class. Tests tool callback
 * wrapping, input augmentation, and argument processing.
 *
 * @author Christian Tzolov
 */
class AugmentedToolCallbackTest {

	@Mock
	private ToolCallback mockDelegate;

	@Mock
	private ToolDefinition mockToolDefinition;

	@Mock
	private ToolContext mockToolContext;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	@Nested
	@DisplayName("Constructor Tests")
	class ConstructorTests {

		@Test
		@DisplayName("Should create callback with valid parameters")
		void shouldCreateCallbackWithValidParameters() {
			// Given
			String originalSchema = """
					{
					  "type": "object",
					  "properties": {
					    "originalField": {
					      "type": "string"
					    }
					  }
					}
					""";

			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.name()).thenReturn("testTool");
			when(mockToolDefinition.description()).thenReturn("Test tool description");
			when(mockToolDefinition.inputSchema()).thenReturn(originalSchema);

			Consumer<AugmentedArgumentEvent<TestArguments>> consumer = args -> {
			};

			// When
			AugmentedToolCallback<TestArguments> callback = new AugmentedToolCallback<>(mockDelegate,
					TestArguments.class, consumer, false);

			// Then
			assertNotNull(callback);
			assertNotNull(callback.getToolDefinition());
			assertEquals("testTool", callback.getToolDefinition().name());
			assertEquals("Test tool description", callback.getToolDefinition().description());
		}

		@Test
		@DisplayName("Should throw exception for null delegate")
		void shouldThrowExceptionForNullDelegate() {
			Consumer<AugmentedArgumentEvent<TestArguments>> consumer = args -> {
			};

			assertThrows(IllegalArgumentException.class,
					() -> new AugmentedToolCallback<>(null, TestArguments.class, consumer, false));
		}

		@Test
		@DisplayName("Should throw exception for null argument class")
		void shouldThrowExceptionForNullArgumentClass() {
			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.inputSchema()).thenReturn("{}");

			Consumer<AugmentedArgumentEvent<TestArguments>> consumer = args -> {
			};

			assertThrows(IllegalArgumentException.class,
					() -> new AugmentedToolCallback<>(mockDelegate, null, consumer, false));
		}

		@Test
		@DisplayName("Should throw exception for non-record class")
		void shouldThrowExceptionForNonRecordClass() {
			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.inputSchema()).thenReturn("{}");

			// Test with a regular class that is not a record
			// We'll use reflection to bypass the generic type checking
			assertThrows(IllegalArgumentException.class, () -> {
				try {
					java.lang.reflect.Constructor<?> constructor = AugmentedToolCallback.class
						.getConstructor(ToolCallback.class, Class.class, Consumer.class, boolean.class);
					constructor.newInstance(mockDelegate, String.class, (Consumer<String>) args -> {
					}, false);
				}
				catch (java.lang.reflect.InvocationTargetException e) {
					if (e.getCause() instanceof IllegalArgumentException) {
						throw (IllegalArgumentException) e.getCause();
					}
					throw new RuntimeException(e.getCause());
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}

		@Test
		@DisplayName("Should throw exception for record with no fields")
		void shouldThrowExceptionForRecordWithNoFields() {
			record EmptyRecord() {
			}

			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.inputSchema()).thenReturn("{}");

			Consumer<AugmentedArgumentEvent<EmptyRecord>> consumer = args -> {
			};

			assertThrows(IllegalArgumentException.class,
					() -> new AugmentedToolCallback<>(mockDelegate, EmptyRecord.class, consumer, false));
		}

	}

	@Nested
	@DisplayName("Tool Definition Tests")
	class ToolDefinitionTests {

		@Test
		@DisplayName("Should augment tool definition schema")
		void shouldAugmentToolDefinitionSchema() throws Exception {
			// Given
			String originalSchema = """
					{
					  "type": "object",
					  "properties": {
					    "originalField": {
					      "type": "string",
					      "description": "Original field"
					    }
					  },
					  "required": ["originalField"]
					}
					""";

			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.name()).thenReturn("testTool");
			when(mockToolDefinition.description()).thenReturn("Test tool description");
			when(mockToolDefinition.inputSchema()).thenReturn(originalSchema);

			Consumer<AugmentedArgumentEvent<TestArguments>> consumer = args -> {
			};

			// When
			AugmentedToolCallback<TestArguments> callback = new AugmentedToolCallback<>(mockDelegate,
					TestArguments.class, consumer, false);

			// Then
			ToolDefinition augmentedDefinition = callback.getToolDefinition();
			String augmentedSchema = augmentedDefinition.inputSchema();

			JsonNode schemaNode = JsonMapper.shared().readTree(augmentedSchema);

			// Check original field is preserved
			assertTrue(schemaNode.get("properties").has("originalField"));

			// Check new fields are added
			assertTrue(schemaNode.get("properties").has("name"));
			assertTrue(schemaNode.get("properties").has("age"));

			// Check descriptions
			assertEquals("Test name field", schemaNode.get("properties").get("name").get("description").asText());
			assertEquals("Test age field", schemaNode.get("properties").get("age").get("description").asText());

			// Check required fields
			JsonNode requiredArray = schemaNode.get("required");
			boolean foundOriginal = false;
			boolean foundName = false;
			for (JsonNode requiredField : requiredArray) {
				String fieldName = requiredField.asText();
				if ("originalField".equals(fieldName)) {
					foundOriginal = true;
				}
				else if ("name".equals(fieldName)) {
					foundName = true;
				}
			}
			assertTrue(foundOriginal);
			assertTrue(foundName);
		}

	}

	@Nested
	@DisplayName("Call Method Tests")
	class CallMethodTests {

		@SuppressWarnings("null")
		@Test
		@DisplayName("Should call delegate with processed input")
		void shouldCallDelegateWithProcessedInput() {
			// Given
			String originalSchema = """
					{
					  "type": "object",
					  "properties": {
					    "originalField": {
					      "type": "string"
					    }
					  }
					}
					""";

			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.name()).thenReturn("testTool");
			when(mockToolDefinition.description()).thenReturn("Test tool description");
			when(mockToolDefinition.inputSchema()).thenReturn(originalSchema);
			when(mockDelegate.call(anyString())).thenReturn("success");

			AtomicReference<AugmentedArgumentEvent<TestArguments>> capturedArgs = new AtomicReference<>();
			Consumer<AugmentedArgumentEvent<TestArguments>> consumer = capturedArgs::set;

			AugmentedToolCallback<TestArguments> callback = new AugmentedToolCallback<>(mockDelegate,
					TestArguments.class, consumer, false);

			String toolInput = """
					{
					  "originalField": "test",
					  "name": "John",
					  "age": 30
					}
					""";

			// When
			String result = callback.call(toolInput);

			// Then
			assertEquals("success", result);
			verify(mockDelegate).call(toolInput);

			TestArguments args = capturedArgs.get().arguments();
			assertNotNull(args);
			assertEquals("John", args.name());
			assertEquals(30, args.age());
		}

		@Test
		@DisplayName("Should call delegate with context")
		void shouldCallDelegateWithContext() {
			// Given
			String originalSchema = """
					{
					  "type": "object",
					  "properties": {
					    "originalField": {
					      "type": "string"
					    }
					  }
					}
					""";

			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.name()).thenReturn("testTool");
			when(mockToolDefinition.description()).thenReturn("Test tool description");
			when(mockToolDefinition.inputSchema()).thenReturn(originalSchema);
			when(mockDelegate.call(anyString(), any(ToolContext.class))).thenReturn("success");

			Consumer<AugmentedArgumentEvent<TestArguments>> consumer = args -> {
			};

			AugmentedToolCallback<TestArguments> callback = new AugmentedToolCallback<>(mockDelegate,
					TestArguments.class, consumer, false);

			String toolInput = """
					{
					  "originalField": "test",
					  "name": "John",
					  "age": 30
					}
					""";

			// When
			String result = callback.call(toolInput, mockToolContext);

			// Then
			assertEquals("success", result);
			verify(mockDelegate).call(toolInput, mockToolContext);
		}

		@SuppressWarnings("null")
		@Test
		@DisplayName("Should remove extended arguments when configured")
		void shouldRemoveExtendedArgumentsWhenConfigured() throws Exception {
			// Given
			String originalSchema = """
					{
					  "type": "object",
					  "properties": {
					    "originalField": {
					      "type": "string"
					    }
					  }
					}
					""";

			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.name()).thenReturn("testTool");
			when(mockToolDefinition.description()).thenReturn("Test tool description");
			when(mockToolDefinition.inputSchema()).thenReturn(originalSchema);
			when(mockDelegate.call(anyString())).thenReturn("success");

			Consumer<AugmentedArgumentEvent<TestArguments>> consumer = args -> {
			};

			AugmentedToolCallback<TestArguments> callback = new AugmentedToolCallback<>(mockDelegate,
					TestArguments.class, consumer, true);

			String toolInput = """
					{
					  "originalField": "test",
					  "name": "John",
					  "age": 30
					}
					""";

			// When
			callback.call(toolInput);

			// Then
			verify(mockDelegate).call(argThat(input -> {
				try {
					JsonNode inputNode = JsonMapper.shared().readTree(input);
					return inputNode.has("originalField") && !inputNode.has("name") && !inputNode.has("age");
				}
				catch (Exception e) {
					return false;
				}
			}));
		}

		@SuppressWarnings("null")
		@Test
		@DisplayName("Should preserve extended arguments when not configured to remove")
		void shouldPreserveExtendedArgumentsWhenNotConfiguredToRemove() throws Exception {
			// Given
			String originalSchema = """
					{
					  "type": "object",
					  "properties": {
					    "originalField": {
					      "type": "string"
					    }
					  }
					}
					""";

			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.name()).thenReturn("testTool");
			when(mockToolDefinition.description()).thenReturn("Test tool description");
			when(mockToolDefinition.inputSchema()).thenReturn(originalSchema);
			when(mockDelegate.call(anyString())).thenReturn("success");

			Consumer<AugmentedArgumentEvent<TestArguments>> consumer = args -> {
			};

			AugmentedToolCallback<TestArguments> callback = new AugmentedToolCallback<>(mockDelegate,
					TestArguments.class, consumer, false);

			String toolInput = """
					{
					  "originalField": "test",
					  "name": "John",
					  "age": 30
					}
					""";

			// When
			callback.call(toolInput);

			// Then
			verify(mockDelegate).call(argThat(input -> {
				try {
					JsonNode inputNode = JsonMapper.shared().readTree(input);
					return inputNode.has("originalField") && inputNode.has("name") && inputNode.has("age");
				}
				catch (Exception e) {
					return false;
				}
			}));
		}

		@SuppressWarnings("null")
		@Test
		@DisplayName("Should handle null consumer gracefully")
		void shouldHandleNullConsumerGracefully() {
			// Given
			String originalSchema = """
					{
					  "type": "object",
					  "properties": {
					    "originalField": {
					      "type": "string"
					    }
					  }
					}
					""";

			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.name()).thenReturn("testTool");
			when(mockToolDefinition.description()).thenReturn("Test tool description");
			when(mockToolDefinition.inputSchema()).thenReturn(originalSchema);
			when(mockDelegate.call(anyString())).thenReturn("success");

			AugmentedToolCallback<TestArguments> callback = new AugmentedToolCallback<>(mockDelegate,
					TestArguments.class, null, false);

			String toolInput = """
					{
					  "originalField": "test",
					  "name": "John",
					  "age": 30
					}
					""";

			// When & Then - should not throw exception
			assertDoesNotThrow(() -> {
				String result = callback.call(toolInput);
				assertEquals("success", result);
			});
		}

	}

	@Nested
	@DisplayName("Integration Tests")
	class IntegrationTests {

		@SuppressWarnings("null")
		@Test
		@DisplayName("Should handle complete workflow with consumer processing")
		void shouldHandleCompleteWorkflowWithConsumerProcessing() {
			// Given
			String originalSchema = """
					{
					  "type": "object",
					  "properties": {
					    "productId": {
					      "type": "integer",
					      "description": "Product identifier"
					    }
					  },
					  "required": ["productId"]
					}
					""";

			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.name()).thenReturn("productTool");
			when(mockToolDefinition.description()).thenReturn("Product management tool");
			when(mockToolDefinition.inputSchema()).thenReturn(originalSchema);
			when(mockDelegate.call(anyString())).thenReturn("Product processed successfully");

			AtomicReference<AugmentedArgumentEvent<SimpleArguments>> processedArgs = new AtomicReference<>();
			Consumer<AugmentedArgumentEvent<SimpleArguments>> consumer = processedArgs::set;

			AugmentedToolCallback<SimpleArguments> callback = new AugmentedToolCallback<>(mockDelegate,
					SimpleArguments.class, consumer, true);

			String toolInput = """
					{
					  "productId": 12345,
					  "value": "Special Product"
					}
					""";

			// When
			String result = callback.call(toolInput);

			// Then
			assertEquals("Product processed successfully", result);

			// Verify consumer was called with correct arguments
			SimpleArguments args = processedArgs.get().arguments();
			assertNotNull(args);
			assertEquals("Special Product", args.value());

			// Verify delegate was called with cleaned input (extended args removed)
			verify(mockDelegate).call(argThat(input -> {
				try {
					JsonNode inputNode = JsonMapper.shared().readTree(input);
					return inputNode.has("productId") && !inputNode.has("value");
				}
				catch (Exception e) {
					return false;
				}
			}));
		}

		@Test
		@DisplayName("Should preserve tool definition metadata")
		void shouldPreserveToolDefinitionMetadata() {
			// Given
			String originalSchema = """
					{
						"type":"object",
						"properties":{
							"field1":{
								"type": "string"
							}
						}
					}
					""";

			when(mockDelegate.getToolDefinition()).thenReturn(mockToolDefinition);
			when(mockToolDefinition.name()).thenReturn("myTool");
			when(mockToolDefinition.description()).thenReturn("My custom tool");
			when(mockToolDefinition.inputSchema()).thenReturn(originalSchema);

			Consumer<AugmentedArgumentEvent<SimpleArguments>> consumer = args -> {
			};

			// When
			AugmentedToolCallback<SimpleArguments> callback = new AugmentedToolCallback<>(mockDelegate,
					SimpleArguments.class, consumer, false);

			// Then
			ToolDefinition definition = callback.getToolDefinition();
			assertEquals("myTool", definition.name());
			assertEquals("My custom tool", definition.description());

			// Schema should be augmented but preserve original structure
			assertNotEquals(originalSchema, definition.inputSchema());
			assertTrue(definition.inputSchema().contains("field1"));
			assertTrue(definition.inputSchema().contains("value"));
		}

	}

	// Test record classes
	public record TestArguments(@ToolParam(description = "Test name field", required = true) String name,
			@ToolParam(description = "Test age field", required = false) int age) {
	}

	public record SimpleArguments(@ToolParam(description = "Simple field", required = true) String value) {
	}

}

package org.springframework.ai.tool.method;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MethodToolCallbackProvider}.
 *
 * @author Thomas Vitale
 */
class MethodToolCallbackProviderTests {

	@Nested
	class BuilderValidationTests {

		@Test
		void shouldRejectNullToolObjects() {
			assertThatThrownBy(() -> MethodToolCallbackProvider.builder().toolObjects((Object[]) null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("toolObjects cannot be null");
		}

		@Test
		void shouldRejectNullToolObjectElements() {
			assertThatThrownBy(() -> MethodToolCallbackProvider.builder().toolObjects(new Tools(), null).build())
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("toolObjects cannot contain null elements");
		}

		@Test
		void shouldAcceptEmptyToolObjects() {
			var provider = MethodToolCallbackProvider.builder().toolObjects().build();
			assertThat(provider.getToolCallbacks()).isEmpty();
		}

	}

	@Test
	void shouldProvideToolCallbacksFromObject() {
		Tools tools = new Tools();
		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(tools).build();

		ToolCallback[] callbacks = provider.getToolCallbacks();

		assertThat(callbacks).hasSize(2);

		var callback1 = Stream.of(callbacks).filter(c -> c.getName().equals("testMethod")).findFirst();
		assertThat(callback1).isPresent();
		assertThat(callback1.get().getName()).isEqualTo("testMethod");
		assertThat(callback1.get().getDescription()).isEqualTo("Test description");

		var callback2 = Stream.of(callbacks).filter(c -> c.getName().equals("testStaticMethod")).findFirst();
		assertThat(callback2).isPresent();
		assertThat(callback2.get().getName()).isEqualTo("testStaticMethod");
		assertThat(callback2.get().getDescription()).isEqualTo("Test description");
	}

	@Test
	void shouldProvideToolCallbacksFromMultipleObjects() {
		Tools tools1 = new Tools();
		ToolsExtra tools2 = new ToolsExtra();

		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(tools1, tools2).build();

		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(4); // 2 from Tools + 2 from ToolsExtra

		assertThat(Stream.of(callbacks).map(ToolCallback::getName)).containsExactlyInAnyOrder("testMethod",
				"testStaticMethod", "extraMethod1", "extraMethod2");
	}

	@Test
	void shouldEnsureUniqueToolNames() {
		ToolsWithDuplicates testComponent = new ToolsWithDuplicates();
		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(testComponent).build();

		assertThatThrownBy(provider::getToolCallbacks).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Multiple tools with the same name (testMethod) found in sources: "
					+ testComponent.getClass().getName());
	}

	@Test
	void shouldHandleToolMethodsWithDifferentVisibility() {
		ToolsWithVisibility tools = new ToolsWithVisibility();
		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(tools).build();

		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(3);

		assertThat(Stream.of(callbacks).map(ToolCallback::getName)).containsExactlyInAnyOrder("publicMethod",
				"protectedMethod", "privateMethod");
	}

	@Test
	void shouldHandleToolMethodsWithDifferentParameters() {
		ToolsWithParameters tools = new ToolsWithParameters();
		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(tools).build();

		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(3);

		assertThat(Stream.of(callbacks).map(ToolCallback::getName)).containsExactlyInAnyOrder("noParams", "oneParam",
				"multipleParams");
	}

	@Test
	void shouldHandleToolMethodsWithDifferentReturnTypes() {
		ToolsWithReturnTypes tools = new ToolsWithReturnTypes();
		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(tools).build();

		ToolCallback[] callbacks = provider.getToolCallbacks();
		assertThat(callbacks).hasSize(4);

		assertThat(Stream.of(callbacks).map(ToolCallback::getName)).containsExactlyInAnyOrder("voidMethod",
				"primitiveMethod", "objectMethod", "collectionMethod");
	}

	static class Tools {

		@Tool("Test description")
		static List<String> testStaticMethod(String input) {
			return List.of(input);
		}

		@Tool("Test description")
		List<String> testMethod(String input) {
			return List.of(input);
		}

		@Tool("Test description")
		Function<String, Integer> testFunction(String input) {
			// This method should be ignored as it's a functional type
			return String::length;
		}

		@Tool("Test description")
		Consumer<String> testConsumer(String input) {
			// This method should be ignored as it's a functional type
			return System.out::println;
		}

		@Tool("Test description")
		Supplier<String> testSupplier() {
			// This method should be ignored as it's a functional type
			return () -> "test";
		}

		void nonToolMethod() {
			// This method should be ignored as it doesn't have @Tool annotation
		}

	}

	static class ToolsExtra {

		@Tool("Extra method 1")
		String extraMethod1() {
			return "extra1";
		}

		@Tool("Extra method 2")
		String extraMethod2() {
			return "extra2";
		}

	}

	static class ToolsWithDuplicates {

		@Tool(name = "testMethod", value = "Test description")
		List<String> testMethod1(String input) {
			return List.of(input);
		}

		@Tool(name = "testMethod", value = "Test description")
		List<String> testMethod2(String input) {
			return List.of(input);
		}

	}

	static class ToolsWithVisibility {

		@Tool("Public method")
		public String publicMethod() {
			return "public";
		}

		@Tool("Protected method")
		protected String protectedMethod() {
			return "protected";
		}

		@Tool("Private method")
		private String privateMethod() {
			return "private";
		}

	}

	static class ToolsWithParameters {

		@Tool("No parameters")
		String noParams() {
			return "no params";
		}

		@Tool("One parameter")
		String oneParam(String param) {
			return param;
		}

		@Tool("Multiple parameters")
		String multipleParams(String param1, int param2, boolean param3) {
			return param1 + param2 + param3;
		}

	}

	static class ToolsWithReturnTypes {

		@Tool("Void method")
		void voidMethod() {
		}

		@Tool("Primitive method")
		int primitiveMethod() {
			return 42;
		}

		@Tool("Object method")
		String objectMethod() {
			return "object";
		}

		@Tool("Collection method")
		List<String> collectionMethod() {
			return List.of("collection");
		}

	}

}

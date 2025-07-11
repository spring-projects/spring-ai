package org.springframework.ai.tool.method;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;

import static org.junit.jupiter.api.Assertions.*;

class MethodToolCallbackProviderTest {

	abstract class TestObjectClass<T> {

		public abstract String test(T input);
	}

	class TestObjectSuperClass extends TestObjectClass<String> {

		@Tool
		public String test(String input) {
			return input;
		}
	}

	@Test
	public void buildToolsWithBridgeMethodReturnOnlyUserDeclaredMethods() {
		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder().toolObjects(new TestObjectSuperClass()).build();
		ToolCallback[] toolCallbacks = provider.getToolCallbacks();
		assertEquals(1, toolCallbacks.length);
		assertInstanceOf(MethodToolCallback.class, toolCallbacks[0]);
	}

}

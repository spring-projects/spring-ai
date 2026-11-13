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

package org.springframework.ai.mcp.annotation.provider.complete;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.modern.server.McpAsyncNotifier;
import io.modelcontextprotocol.modern.server.feature.McpAsyncCompletionHandler;
import io.modelcontextprotocol.util.Assert;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.method.complete.AsyncModernMcpCompleteMethodCallback;

/**
 * The asynchronous twin of {@link SyncModernMcpCompleteProvider}: creates the completion
 * handlers of a 2026-07-28 ("modern") MCP server from methods annotated with
 * {@link McpComplete}. Each method is served by an
 * {@link AsyncModernMcpCompleteMethodCallback}.
 * <p>
 * A handler completes every argument of the prompt or resource template that its
 * annotation names, and answers only once it is attached to that prompt or template with
 * {@code McpAsyncPrompt.withCompletionHandler} or
 * {@code AsyncResourceTemplateRegistration.withCompletionHandler}. The Spring entry
 * points attach them to the annotated prompts and templates.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class AsyncModernMcpCompleteProvider {

	private final List<Object> completeObjects;

	public AsyncModernMcpCompleteProvider(List<Object> completeObjects) {
		Assert.notNull(completeObjects, "completeObjects cannot be null");
		this.completeObjects = completeObjects;
	}

	/**
	 * The handlers of the {@code @McpComplete(prompt = ...)} methods, keyed by prompt
	 * name.
	 * @throws IllegalArgumentException if two methods complete the same prompt
	 */
	public Map<String, McpAsyncCompletionHandler> getPromptCompletions() {
		return completions(true);
	}

	/**
	 * The handlers of the {@code @McpComplete(uri = ...)} methods, keyed by URI template.
	 * @throws IllegalArgumentException if two methods complete the same URI template
	 */
	public Map<String, McpAsyncCompletionHandler> getResourceCompletions() {
		return completions(false);
	}

	private Map<String, McpAsyncCompletionHandler> completions(boolean prompts) {
		return ModernCompleteMethods.collect(this.completeObjects, this::doGetClassMethods,
				ModernMethodSupport.filterLegacyContextMethods(McpAsyncNotifier.class), prompts,
				(bean, method) -> AsyncModernMcpCompleteMethodCallback.builder()
					.method(method)
					.bean(bean)
					.complete(method.getAnnotation(McpComplete.class))
					.build());
	}

	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

}

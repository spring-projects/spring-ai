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

package org.springframework.ai.mcp.client.common.autoconfigure;

import io.micrometer.context.ContextRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.util.context.ContextView;

/**
 * Loads the registry of the context-propagation library, with which Reactor restores
 * thread locals, for the modern MCP connections.
 * <p>
 * The registry is created once, by the thread that touches it first, with the accessors
 * that the context class loader of that thread sees. In an executable jar a thread of the
 * common {@code ForkJoinPool} does not see the accessor of the Reactor context. A
 * registry created there stays without it, and every request of a modern connection fails
 * from then on, on any thread.
 *
 * @author Christian Tzolov
 * @since 2.1.1
 */
final class ModernMcpContextRegistry {

	private static final Log logger = LogFactory.getLog(ModernMcpContextRegistry.class);

	private ModernMcpContextRegistry() {
	}

	/**
	 * Creates the registry with the given class loader if no thread has done so, and
	 * loads its accessors with that class loader if it does not read the Reactor context,
	 * as a registry that was created by a thread which did not see them.
	 * @param classLoader the class loader of the application
	 */
	static void load(@Nullable ClassLoader classLoader) {
		Thread thread = Thread.currentThread();
		ClassLoader original = thread.getContextClassLoader();
		thread.setContextClassLoader(classLoader);
		try {
			ContextRegistry registry = ContextRegistry.getInstance();
			if (!readsReactorContext(registry)) {
				registry.loadContextAccessors().loadThreadLocalAccessors();
			}
		}
		catch (RuntimeException ex) {
			logger.warn("The registry of the context-propagation library could not be loaded", ex);
		}
		finally {
			thread.setContextClassLoader(original);
		}
	}

	static boolean readsReactorContext(ContextRegistry registry) {
		return registry.getContextAccessors()
			.stream()
			.anyMatch(accessor -> accessor.readableType().isAssignableFrom(ContextView.class));
	}

}

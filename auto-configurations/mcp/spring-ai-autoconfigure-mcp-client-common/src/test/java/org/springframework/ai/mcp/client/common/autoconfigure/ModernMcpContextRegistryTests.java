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

import java.util.List;

import io.micrometer.context.ContextAccessor;
import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ModernMcpContextRegistry}.
 *
 * @author Christian Tzolov
 */
class ModernMcpContextRegistryTests {

	private static final ClassLoader APPLICATION_CLASS_LOADER = ModernMcpContextRegistryTests.class.getClassLoader();

	@Test
	void registryWithoutTheAccessorOfTheReactorContextIsLoadedAgain() throws InterruptedException {
		ContextRegistry registry = ContextRegistry.getInstance();
		List<ContextAccessor<?, ?>> removed = removeReactorContextAccessors(registry);
		try {
			assertThat(ModernMcpContextRegistry.readsReactorContext(registry)).isFalse();

			// The thread that configures the connections may not see the accessors
			// either, for example when the beans are created lazily.
			ClassLoader afterwards = ServiceLoaderBlindThread.call(() -> {
				ModernMcpContextRegistry.load(APPLICATION_CLASS_LOADER);
				return Thread.currentThread().getContextClassLoader();
			});

			assertThat(ModernMcpContextRegistry.readsReactorContext(registry)).isTrue();
			// The thread has its own class loader back.
			assertThat(afterwards).isNotNull().isNotSameAs(APPLICATION_CLASS_LOADER);
		}
		finally {
			restoreReactorContextAccessors(registry, removed);
		}
	}

	@Test
	void registryThatReadsTheReactorContextIsLeftAsItIs() {
		ContextRegistry registry = ContextRegistry.getInstance();
		ModernMcpContextRegistry.load(APPLICATION_CLASS_LOADER);
		List<ContextAccessor<?, ?>> before = List.copyOf(registry.getContextAccessors());

		ModernMcpContextRegistry.load(APPLICATION_CLASS_LOADER);

		assertThat(registry.getContextAccessors()).containsExactlyElementsOf(before);
	}

	/**
	 * Leaves the registry as a thread creates it whose context class loader does not see
	 * the accessors: without the accessor of the Reactor context.
	 */
	static List<ContextAccessor<?, ?>> removeReactorContextAccessors(ContextRegistry registry) {
		List<ContextAccessor<?, ?>> reactorContextAccessors = registry.getContextAccessors()
			.stream()
			.filter(accessor -> accessor.readableType().isAssignableFrom(reactor.util.context.ContextView.class))
			.toList();
		reactorContextAccessors.forEach(registry::removeContextAccessor);
		return reactorContextAccessors;
	}

	static void restoreReactorContextAccessors(ContextRegistry registry, List<ContextAccessor<?, ?>> removed) {
		if (!ModernMcpContextRegistry.readsReactorContext(registry)) {
			removed.forEach(registry::registerContextAccessor);
		}
	}

}

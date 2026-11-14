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

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import io.modelcontextprotocol.json.McpJsonDefaults;

/**
 * Runs an action on a thread whose context class loader sees no service of the
 * application, as a thread of the common {@code ForkJoinPool} of an executable jar does
 * not see the nested jars. A JSON mapper that is looked up with the {@code ServiceLoader}
 * on such a thread is not found.
 *
 * @author Christian Tzolov
 */
final class ServiceLoaderBlindThread {

	private ServiceLoaderBlindThread() {
	}

	static <T> T call(Supplier<T> action) throws InterruptedException {
		// Drops the default mapper that the MCP SDK may have looked up already, so
		// that a lookup on this thread starts again.
		new McpJsonDefaults();

		AtomicReference<T> result = new AtomicReference<>();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread thread = new Thread(() -> {
			try {
				result.set(action.get());
			}
			catch (Throwable ex) {
				failure.set(ex);
			}
		});
		thread.setContextClassLoader(new ClassLoader(null) {
		});
		thread.start();
		thread.join();

		if (failure.get() != null) {
			throw new AssertionError("The action failed on a thread that cannot see the services", failure.get());
		}
		return result.get();
	}

}

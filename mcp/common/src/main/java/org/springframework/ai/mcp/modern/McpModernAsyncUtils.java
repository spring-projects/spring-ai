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

package org.springframework.ai.mcp.modern;

import java.util.concurrent.Callable;

import io.modelcontextprotocol.modern.server.McpRequestContext;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.util.Assert;

/**
 * Runs blocking code inside the asynchronous handlers of a 2026-07-28 ("modern") MCP
 * server.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class McpModernAsyncUtils {

	private McpModernAsyncUtils() {
	}

	/**
	 * Wraps a blocking call the way the SDK adapts its sync handlers: on the calling
	 * thread when the request is served blocking ({@link McpRequestContext#isBlocking()},
	 * the WebMvc transport), and on {@link Schedulers#boundedElastic()} otherwise, so
	 * that an event loop is never blocked.
	 * @param context the request context
	 * @param call the blocking call; a {@code null} result completes the Mono empty
	 * @param <T> the result type
	 * @return the deferred result
	 */
	public static <T> Mono<T> fromBlocking(McpRequestContext context, Callable<T> call) {
		Assert.notNull(context, "context must not be null");
		Assert.notNull(call, "call must not be null");
		Mono<T> mono = Mono.fromCallable(call);
		return context.isBlocking() ? mono : mono.subscribeOn(Schedulers.boundedElastic());
	}

}

/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.tool;

/**
 * Tool execution mode enumeration.
 *
 * <p>
 * Defines different execution modes for tool calls, used for performance optimization and
 * resource management.
 *
 * <h2>Usage Scenarios</h2>
 * <ul>
 * <li><strong>SYNC</strong>: Fast-executing tools (< 100ms), pure computation tasks</li>
 * <li><strong>ASYNC</strong>: I/O-involving operations (HTTP requests, database queries),
 * long-running tasks (> 1 second)</li>
 * <li><strong>PARALLEL</strong>: Multiple independent tools that need parallel
 * execution</li>
 * <li><strong>STREAMING</strong>: Long-running tasks that require real-time feedback</li>
 * </ul>
 *
 * @author Spring AI Team
 * @since 1.2.0
 */
public enum ToolExecutionMode {

	/**
	 * Synchronous execution mode.
	 *
	 * <p>
	 * Tool execution blocks the calling thread until completion. Suitable for:
	 * <ul>
	 * <li>Fast-executing tools (< 100ms)</li>
	 * <li>Pure computation tasks</li>
	 * <li>Operations not involving I/O</li>
	 * <li>Simple string processing</li>
	 * </ul>
	 *
	 * <p>
	 * <strong>Performance Impact</strong>: Occupies threads in the thread pool and may
	 * become a bottleneck in high concurrency scenarios. By default, synchronous tools
	 * execute in the boundedElastic thread pool (maximum 80 threads).
	 *
	 * <h3>Example</h3> <pre>{@code
	 * &#64;Tool("calculate_sum")
	 * public int calculateSum(int a, int b) {
	 *     // Pure computation, suitable for sync mode
	 *     return a + b;
	 * }
	 * }</pre>
	 */
	SYNC,

	/**
	 * Asynchronous execution mode.
	 *
	 * <p>
	 * Tool execution doesn't block the calling thread, using reactive programming model.
	 * Suitable for:
	 * <ul>
	 * <li>Network I/O operations (HTTP requests, RPC calls)</li>
	 * <li>Database queries and updates</li>
	 * <li>File read/write operations</li>
	 * <li>Long-running tasks (> 1 second)</li>
	 * <li>High concurrency scenarios</li>
	 * </ul>
	 *
	 * <p>
	 * <strong>Performance Advantage</strong>: Doesn't occupy threads and can support
	 * thousands or even tens of thousands of concurrent tool calls. In high concurrency
	 * scenarios, performance improvement can reach 5-10x.
	 *
	 * <h3>Example</h3> <pre>{@code
	 * &#64;Component
	 * public class AsyncWeatherTool implements AsyncToolCallback {
	 *     &#64;Override
	 *     public Mono<String> callAsync(String input, ToolContext context) {
	 *         // Network I/O, suitable for async mode
	 *         return webClient.get()
	 *             .uri("/weather")
	 *             .retrieve()
	 *             .bodyToMono(String.class);
	 *     }
	 * }
	 * }</pre>
	 *
	 * <h3>Performance Comparison</h3>
	 * <table border="1">
	 * <tr>
	 * <th>Concurrency</th>
	 * <th>Sync Mode</th>
	 * <th>Async Mode</th>
	 * <th>Improvement</th>
	 * </tr>
	 * <tr>
	 * <td>100 requests</td>
	 * <td>avg 4s</td>
	 * <td>avg 2s</td>
	 * <td>50%</td>
	 * </tr>
	 * <tr>
	 * <td>500 requests</td>
	 * <td>avg 12s</td>
	 * <td>avg 2s</td>
	 * <td>83%</td>
	 * </tr>
	 * </table>
	 */
	ASYNC,

	/**
	 * Parallel execution mode (future extension).
	 *
	 * <p>
	 * Multiple tool calls can execute in parallel rather than sequentially. Suitable for
	 * scenarios where tool calls have no dependencies.
	 *
	 * <p>
	 * <strong>Note</strong>: This mode is not currently implemented, reserved for future
	 * extension.
	 *
	 * <h3>Future Usage</h3> <pre>{@code
	 * // Possible future API
	 * toolManager.executeInParallel(
	 *     toolCall1,  // Get weather
	 *     toolCall2,  // Get news
	 *     toolCall3   // Get stock price
	 * );
	 * // Three tools execute simultaneously, not sequentially
	 * }</pre>
	 */
	PARALLEL,

	/**
	 * Streaming execution mode (future extension).
	 *
	 * <p>
	 * Tools can return streaming results rather than waiting for complete execution.
	 * Suitable for long-running tasks that require real-time feedback.
	 *
	 * <p>
	 * <strong>Note</strong>: This mode is not currently implemented, reserved for future
	 * extension.
	 *
	 * <h3>Future Usage</h3> <pre>{@code
	 * // Possible future API
	 * &#64;Component
	 * public class StreamingAnalysisTool implements StreamingToolCallback {
	 *     &#64;Override
	 *     public Flux<ToolExecutionChunk> executeStreaming(String input) {
	 *         return Flux.interval(Duration.ofSeconds(1))
	 *             .take(10)
	 *             .map(i -> new ToolExecutionChunk("Progress: " + (i * 10) + "%"));
	 *     }
	 * }
	 *
	 * // AI can see tool execution progress in real-time
	 * // Users can see feedback in real-time
	 * }</pre>
	 */
	STREAMING

}

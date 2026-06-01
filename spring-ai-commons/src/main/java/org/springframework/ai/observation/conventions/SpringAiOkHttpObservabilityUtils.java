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

package org.springframework.ai.observation.conventions;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpConnectionPoolMetrics;
import io.micrometer.core.instrument.binder.okhttp3.OkHttpObservationInterceptor;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;

/**
 * Utility class for configuring Micrometer observability on OkHttp clients.
 *
 * @author Soby Chacko
 * @author Ilayaperumal Gopinathan
 * @since 2.0.0
 */
public final class SpringAiOkHttpObservabilityUtils {

	private SpringAiOkHttpObservabilityUtils() {
	}

	/**
	 * Configures an OkHttpClient.Builder with Micrometer observability.
	 * @param builder the OkHttpClient builder
	 * @param observationRegistry the observation registry
	 * @param observationName the name of the observation
	 * @param userDispatcherExecutor an optional user-provided executor service for the
	 * dispatcher
	 * @return true if the dispatcher executor was created internally and should be shut
	 * down by the client, false otherwise
	 */
	public static boolean configureObservability(OkHttpClient.Builder builder, ObservationRegistry observationRegistry,
			String observationName, @Nullable ExecutorService userDispatcherExecutor) {

		OkHttpObservationInterceptor observationInterceptor = OkHttpObservationInterceptor
			.builder(observationRegistry, observationName)
			.build();
		builder.addInterceptor(observationInterceptor);

		boolean ownsDispatcherExecutor = userDispatcherExecutor == null;
		ExecutorService dispatcherBase = (userDispatcherExecutor != null) ? userDispatcherExecutor
				: defaultDispatcherExecutor();
		ExecutorService dispatcherExecutor = ContextExecutorService.wrap(dispatcherBase,
				ContextSnapshotFactory.builder().build());
		builder.dispatcher(new Dispatcher(dispatcherExecutor));

		return ownsDispatcherExecutor;
	}

	/**
	 * Binds OkHttp connection pool metrics to the given meter registry.
	 * @param okClient the OkHttpClient
	 * @param meterRegistry the meter registry
	 * @param meterTags the tags to apply to the metrics
	 */
	public static void bindConnectionPoolMetrics(OkHttpClient okClient, @Nullable MeterRegistry meterRegistry,
			Iterable<Tag> meterTags) {
		if (meterRegistry != null) {
			new OkHttpConnectionPoolMetrics(okClient.connectionPool(), meterTags).bindTo(meterRegistry);
		}
	}

	// Replicates OkHttp's default dispatcher so wrapping for context propagation
	// preserves the standard "OkHttp Dispatcher-N" thread names.
	private static ExecutorService defaultDispatcherExecutor() {
		AtomicInteger counter = new AtomicInteger(1);
		ThreadFactory threadFactory = runnable -> {
			Thread thread = new Thread(runnable, "OkHttp Dispatcher-" + counter.getAndIncrement());
			thread.setDaemon(false);
			return thread;
		};
		return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
				threadFactory);
	}

}

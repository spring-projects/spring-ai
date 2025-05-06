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

package org.springframework.ai.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;

/**
 * An {@link ObservationHandler} that can wrap another one and makes the tracing data
 * available for the {@link ObservationHandler#onStop(Observation.Context)} method. This
 * handler can be used in cases where the logging library or needs access to the tracing
 * data (i.e.: log correlation).
 *
 * @param <T> type of handler context
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public class TracingAwareLoggingObservationHandler<T extends Observation.Context> implements ObservationHandler<T> {

	private final ObservationHandler<T> delegate;

	private final Tracer tracer;

	/**
	 * Creates a new instance.
	 * @param delegate ObservationHandler instance to delegate the handler method calls to
	 * @param tracer Tracer instance to create the scope with
	 */
	public TracingAwareLoggingObservationHandler(ObservationHandler<T> delegate, Tracer tracer) {
		this.delegate = delegate;
		this.tracer = tracer;
	}

	@Override
	public void onStart(T context) {
		this.delegate.onStart(context);
	}

	@Override
	public void onError(T context) {
		this.delegate.onError(context);
	}

	@Override
	public void onEvent(Observation.Event event, T context) {
		this.delegate.onEvent(event, context);
	}

	@Override
	public void onScopeOpened(T context) {
		this.delegate.onScopeOpened(context);
	}

	@Override
	public void onScopeClosed(T context) {
		this.delegate.onScopeClosed(context);
	}

	@Override
	public void onScopeReset(T context) {
		this.delegate.onScopeReset(context);
	}

	@Override
	public void onStop(T context) {
		TracingObservationHandler.TracingContext tracingContext = context
			.getRequired(TracingObservationHandler.TracingContext.class);
		Span currentSpan = tracingContext.getSpan();
		if (currentSpan != null) {
			try (CurrentTraceContext.Scope ignored = tracer.currentTraceContext().maybeScope(currentSpan.context())) {
				this.delegate.onStop(context);
			}
		}
		else {
			this.delegate.onStop(context);
		}
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return this.delegate.supportsContext(context);
	}

}

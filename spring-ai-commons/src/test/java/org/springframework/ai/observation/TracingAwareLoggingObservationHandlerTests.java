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
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Tests for {@link TracingAwareLoggingObservationHandler}.
 *
 * @author Jonatan Ivanov
 */
@ExtendWith(MockitoExtension.class)
class TracingAwareLoggingObservationHandlerTests {

	@Mock
	private ObservationHandler<Observation.Context> delegate;

	@Mock
	private Tracer tracer;

	@InjectMocks
	private TracingAwareLoggingObservationHandler<Observation.Context> handler;

	@Test
	void callsShouldBeDelegated() {
		Observation.Context context = new Observation.Context();
		context.put(TracingObservationHandler.TracingContext.class, new TracingObservationHandler.TracingContext());

		handler.onStart(context);
		verify(delegate).onStart(context);

		handler.onError(context);
		verify(delegate).onError(context);

		Observation.Event event = Observation.Event.of("test");
		handler.onEvent(event, context);
		verify(delegate).onEvent(event, context);

		handler.onScopeOpened(context);
		verify(delegate).onScopeOpened(context);

		handler.onStop(context);
		verify(delegate).onStop(context);

		handler.onScopeClosed(context);
		verify(delegate).onScopeClosed(context);

		handler.onScopeReset(context);
		verify(delegate).onScopeReset(context);

		handler.supportsContext(context);
		verify(delegate).supportsContext(context);
	}

	@Test
	void spanShouldBeAvailableOnStop() {
		Observation.Context observationContext = new Observation.Context();
		TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
		observationContext.put(TracingObservationHandler.TracingContext.class, tracingContext);

		Span span = mock(Span.class);
		tracingContext.setSpan(span);
		TraceContext traceContext = mock(TraceContext.class);
		CurrentTraceContext currentTraceContext = mock(CurrentTraceContext.class);
		CurrentTraceContext.Scope scope = mock(CurrentTraceContext.Scope.class);

		when(span.context()).thenReturn(traceContext);
		when(tracer.currentTraceContext()).thenReturn(currentTraceContext);
		when(currentTraceContext.maybeScope(traceContext)).thenReturn(scope);

		handler.onStop(observationContext);

		verify(scope).close();
		verify(currentTraceContext).maybeScope(traceContext);
		verify(delegate).onStop(observationContext);
	}

}

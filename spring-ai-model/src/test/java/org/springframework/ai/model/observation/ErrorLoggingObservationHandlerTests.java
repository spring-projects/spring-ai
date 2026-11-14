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

package org.springframework.ai.model.observation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ErrorLoggingObservationHandler}.
 *
 * @author DDINGJOO
 */
@ExtendWith(OutputCaptureExtension.class)
class ErrorLoggingObservationHandlerTests {

	private final Tracer tracer = mock(Tracer.class);

	private final List<Observation.Context> consumed = new ArrayList<>();

	private final Consumer<Observation.Context> errorConsumer = this.consumed::add;

	private final ErrorLoggingObservationHandler handler = new ErrorLoggingObservationHandler(this.tracer,
			List.of(SupportedContext.class), this.errorConsumer);

	@Test
	void whenTracerIsNullThenThrow() {
		assertThatThrownBy(() -> new ErrorLoggingObservationHandler(null, List.of(), this.errorConsumer))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Tracer must not be null");
	}

	@Test
	void whenSupportedContextTypesIsNullThenThrow() {
		assertThatThrownBy(() -> new ErrorLoggingObservationHandler(this.tracer, null, this.errorConsumer))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("SupportedContextTypes must not be null");
	}

	@Test
	void whenErrorConsumerIsNullThenThrow() {
		assertThatThrownBy(() -> new ErrorLoggingObservationHandler(this.tracer, List.of(), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("ErrorConsumer must not be null");
	}

	@Test
	void whenContextIsNullThenNotSupported() {
		assertThat(this.handler.supportsContext(null)).isFalse();
	}

	@Test
	void whenContextIsOfSupportedTypeThenSupported() {
		assertThat(this.handler.supportsContext(new SupportedContext())).isTrue();
	}

	@Test
	void whenContextIsSubclassOfSupportedTypeThenSupported() {
		assertThat(this.handler.supportsContext(new SupportedSubContext())).isTrue();
	}

	@Test
	void whenContextIsOfUnrelatedTypeThenNotSupported() {
		assertThat(this.handler.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void whenContextIsNullThenErrorIsIgnored() {
		this.handler.onError(null);

		assertThat(this.consumed).isEmpty();
		verify(this.tracer, never()).withSpan(any());
	}

	@Test
	void whenNoTracingContextThenErrorIsNotConsumed() {
		SupportedContext context = new SupportedContext();
		context.setError(new IllegalStateException("boom"));

		this.handler.onError(context);

		assertThat(this.consumed).isEmpty();
		verify(this.tracer, never()).withSpan(any());
	}

	@Test
	void whenTracingContextPresentThenErrorIsConsumedWithinSpanScope() {
		Span span = mock(Span.class);
		Tracer.SpanInScope scope = mock(Tracer.SpanInScope.class);
		given(this.tracer.withSpan(span)).willReturn(scope);

		SupportedContext context = contextWithSpan(span, new IllegalStateException("boom"));

		this.handler.onError(context);

		assertThat(this.consumed).containsExactly(context);
		InOrder order = inOrder(this.tracer, scope);
		order.verify(this.tracer).withSpan(span);
		order.verify(scope).close();
	}

	@Test
	void whenUsingDefaultConsumerThenErrorIsLogged(CapturedOutput output) {
		Span span = mock(Span.class);
		given(this.tracer.withSpan(span)).willReturn(mock(Tracer.SpanInScope.class));
		ErrorLoggingObservationHandler defaultHandler = new ErrorLoggingObservationHandler(this.tracer,
				List.of(SupportedContext.class));

		defaultHandler.onError(contextWithSpan(span, new IllegalStateException("boom")));

		assertThat(output).contains("Traced Error: ").contains("boom");
	}

	private static SupportedContext contextWithSpan(Span span, Throwable error) {
		TracingContext tracingContext = new TracingContext();
		tracingContext.setSpan(span);
		SupportedContext context = new SupportedContext();
		context.put(TracingContext.class, tracingContext);
		context.setError(error);
		return context;
	}

	static class SupportedContext extends Observation.Context {

	}

	static class SupportedSubContext extends SupportedContext {

	}

}

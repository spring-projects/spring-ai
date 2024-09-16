package org.springframework.ai.observation.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;

/**
 * Unit tests for {@link TracingHelper}.
 *
 * @author Thomas Vitale
 */
class TracingHelperTests {

	@Test
	void extractOtelSpanWhenTracingContextIsNull() {
		var actualOtelSpan = TracingHelper.extractOtelSpan(null);
		assertThat(actualOtelSpan).isNull();
	}

	@Test
	void extractOtelSpanWhenMethodDoesNotExist() {
		var tracingContext = new TracingObservationHandler.TracingContext();
		tracingContext.setSpan(Span.NOOP);
		var actualOtelSpan = TracingHelper.extractOtelSpan(tracingContext);
		assertThat(actualOtelSpan).isNull();
	}

	@Test
	void extractOtelSpanWhenSpanIsNotOpenTelemetry() {
		var tracingContext = new TracingObservationHandler.TracingContext();
		tracingContext.setSpan(new DemoOtherSpan());
		var actualOtelSpan = TracingHelper.extractOtelSpan(tracingContext);
		assertThat(actualOtelSpan).isNull();
	}

	@Test
	void extractOtelSpanWhenSpanIsOpenTelemetry() {
		var tracingContext = new TracingObservationHandler.TracingContext();
		var otelTracer = new OtelTracer(OpenTelemetry.noop().getTracer("test"), new OtelCurrentTraceContext(), null);
		tracingContext.setSpan(otelTracer.nextSpan());
		var actualOtelSpan = TracingHelper.extractOtelSpan(tracingContext);
		assertThat(actualOtelSpan).isNotNull();
		assertThat(actualOtelSpan).isInstanceOf(io.opentelemetry.api.trace.Span.class);
	}

	static class DemoOtherSpan implements Span {

		private static Span toOtel(Span span) {
			return Span.NOOP;
		}

		@Override
		public boolean isNoop() {
			return false;
		}

		@Override
		public TraceContext context() {
			return null;
		}

		@Override
		public Span start() {
			return null;
		}

		@Override
		public Span name(String s) {
			return null;
		}

		@Override
		public Span event(String s) {
			return null;
		}

		@Override
		public Span event(String s, long l, TimeUnit timeUnit) {
			return null;
		}

		@Override
		public Span tag(String s, String s1) {
			return null;
		}

		@Override
		public Span error(Throwable throwable) {
			return null;
		}

		@Override
		public void end() {

		}

		@Override
		public void end(long l, TimeUnit timeUnit) {

		}

		@Override
		public void abandon() {

		}

		@Override
		public Span remoteServiceName(String s) {
			return null;
		}

		@Override
		public Span remoteIpAndPort(String s, int i) {
			return null;
		}

	}

}
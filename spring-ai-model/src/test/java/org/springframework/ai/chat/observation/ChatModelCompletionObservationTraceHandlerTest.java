package org.springframework.ai.chat.observation;

import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.trace.AiObservationContentFormatterName;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiObservationEventNames;
import org.springframework.ai.observation.tracing.TracingHelper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatModelCompletionObservationTraceHandler}.
 *
 * @author tingchuan.li
 */
class ChatModelCompletionObservationTraceHandlerTest {

	static TracingObservationHandler.TracingContext createTracingContext() {
		var sdkTracer = SdkTracerProvider.builder().build().get("test");
		var otelTracer = new OtelTracer(sdkTracer, new OtelCurrentTraceContext(), null);
		var span = otelTracer.nextSpan();
		var tracingContext = new TracingObservationHandler.TracingContext();
		tracingContext.setSpan(span);
		return tracingContext;
	}

	static ChatModelObservationContext createChatModelObservationContext(
			TracingObservationHandler.TracingContext tracingContext) {
		var observationContext = ChatModelObservationContext.builder()
			.prompt(new Prompt("supercalifragilisticexpialidocious",
					ChatOptions.builder().model("spoonful-of-sugar").build()))
			.provider("mary-poppins")
			.build();
		observationContext.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("say please")),
				new Generation(new AssistantMessage("seriously, say please")))));
		observationContext.put(TracingObservationHandler.TracingContext.class, tracingContext);
		return observationContext;
	}

	@Test
	void whenCompletionWithTextThenSpanEvent() {
		var tracingContext = createTracingContext();
		var observationContext = createChatModelObservationContext(tracingContext);
		new ChatModelCompletionObservationTraceHandler(AiObservationContentFormatterName.TEXT)
			.onStop(observationContext);
		var otelSpan = TracingHelper.extractOtelSpan(tracingContext);
		assertThat(otelSpan).isNotNull();
		var spanData = ((ReadableSpan) otelSpan).toSpanData();
		assertThat(spanData.getEvents().size()).isEqualTo(1);
		assertThat(spanData.getEvents().get(0).getName()).isEqualTo(AiObservationEventNames.CONTENT_COMPLETION.value());
		assertThat(spanData.getEvents()
			.get(0)
			.getAttributes()
			.get(AttributeKey.stringArrayKey(AiObservationAttributes.COMPLETION.value())))
			.containsOnly("say please", "seriously, say please");
	}

	@Test
	void whenCompletionWithLangfuseThenSpanEvent() {
		var tracingContext = createTracingContext();
		var observationContext = createChatModelObservationContext(tracingContext);
		new ChatModelCompletionObservationTraceHandler(AiObservationContentFormatterName.LANGFUSE)
			.onStop(observationContext);
		var otelSpan = TracingHelper.extractOtelSpan(tracingContext);
		assertThat(otelSpan).isNotNull();
		var spanData = ((ReadableSpan) otelSpan).toSpanData();
		assertThat(spanData.getEvents().size()).isEqualTo(1);
		assertThat(spanData.getEvents().get(0).getName()).isEqualTo(AiObservationEventNames.CONTENT_COMPLETION.value());
		assertThat(spanData.getEvents()
			.get(0)
			.getAttributes()
			.get(AttributeKey.stringArrayKey(AiObservationAttributes.COMPLETION.value())))
			.containsOnly("{\"role\":\"assistant\",\"content\":\"say please\"}",
					"{\"role\":\"assistant\",\"content\":\"seriously, say please\"}");
	}

}

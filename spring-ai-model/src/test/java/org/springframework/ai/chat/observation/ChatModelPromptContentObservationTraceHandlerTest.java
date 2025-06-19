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
 * Unit tests for {@link ChatModelPromptContentObservationTraceHandler}.
 *
 * @author tingchuan.li
 */
class ChatModelPromptContentObservationTraceHandlerTest {

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
	void whenPromptWithTextThenSpanEvent() {
		var tracingContext = createTracingContext();
		var observationContext = createChatModelObservationContext(tracingContext);
		new ChatModelPromptContentObservationTraceHandler(AiObservationContentFormatterName.TEXT, -1)
			.onStop(observationContext);
		var otelSpan = TracingHelper.extractOtelSpan(tracingContext);
		assertThat(otelSpan).isNotNull();
		var spanData = ((ReadableSpan) otelSpan).toSpanData();
		assertThat(spanData.getEvents().size()).isEqualTo(1);
		assertThat(spanData.getEvents().get(0).getName()).isEqualTo(AiObservationEventNames.CONTENT_PROMPT.value());
		assertThat(spanData.getEvents()
			.get(0)
			.getAttributes()
			.get(AttributeKey.stringArrayKey(AiObservationAttributes.PROMPT.value())))
			.containsOnly("supercalifragilisticexpialidocious");
	}

	@Test
	void whenPromptWithLangfuseThenSpanEvent() {
		var tracingContext = createTracingContext();
		var observationContext = createChatModelObservationContext(tracingContext);
		new ChatModelPromptContentObservationTraceHandler(AiObservationContentFormatterName.LANGFUSE, -1)
			.onStop(observationContext);
		var otelSpan = TracingHelper.extractOtelSpan(tracingContext);
		assertThat(otelSpan).isNotNull();
		var spanData = ((ReadableSpan) otelSpan).toSpanData();
		assertThat(spanData.getEvents().size()).isEqualTo(1);
		assertThat(spanData.getEvents().get(0).getName()).isEqualTo(AiObservationEventNames.CONTENT_PROMPT.value());
		assertThat(spanData.getEvents()
			.get(0)
			.getAttributes()
			.get(AttributeKey.stringArrayKey(AiObservationAttributes.PROMPT.value())))
			.containsOnly("{\"role\":\"user\",\"content\":\"supercalifragilisticexpialidocious\"}");
	}

}

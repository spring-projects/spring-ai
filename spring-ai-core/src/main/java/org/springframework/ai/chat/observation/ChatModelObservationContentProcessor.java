/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.observation;

import io.micrometer.tracing.handler.TracingObservationHandler;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.Content;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.StringJoiner;

/**
 * Utilities to process the prompt and completion content in observations for chat models.
 *
 * @author Thomas Vitale
 */
public final class ChatModelObservationContentProcessor {

	private static final Logger logger = LoggerFactory.getLogger(ChatModelObservationContentProcessor.class);

	public static List<String> prompt(ChatModelObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getInstructions())) {
			return List.of();
		}

		return context.getRequest().getInstructions().stream().map(Content::getContent).toList();
	}

	public static List<String> completion(ChatModelObservationContext context) {
		if (context == null || context.getResponse() == null || context.getResponse().getResults() == null
				|| CollectionUtils.isEmpty(context.getResponse().getResults())) {
			return List.of();
		}

		if (!StringUtils.hasText(context.getResponse().getResult().getOutput().getContent())) {
			return List.of();
		}

		return context.getResponse()
			.getResults()
			.stream()
			.filter(generation -> generation.getOutput() != null
					&& StringUtils.hasText(generation.getOutput().getContent()))
			.map(generation -> generation.getOutput().getContent())
			.toList();
	}

	public static String concatenateStrings(List<String> strings) {
		var promptMessagesJoiner = new StringJoiner(", ", "[", "]");
		strings.forEach(string -> promptMessagesJoiner.add("\"" + string + "\""));
		return promptMessagesJoiner.toString();
	}

	@Nullable
	public static Span extractOtelSpan(@Nullable TracingObservationHandler.TracingContext tracingContext) {
		if (tracingContext == null) {
			return null;
		}

		io.micrometer.tracing.Span micrometerSpan = tracingContext.getSpan();
		try {
			Method toOtelMethod = tracingContext.getSpan()
				.getClass()
				.getDeclaredMethod("toOtel", io.micrometer.tracing.Span.class);
			toOtelMethod.setAccessible(true);
			Object otelSpanObject = toOtelMethod.invoke(null, micrometerSpan);
			if (otelSpanObject instanceof Span otelSpan) {
				return otelSpan;
			}
		}
		catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
			logger.warn("It wasn't possible to extract the OpenTelemetry Span object from Micrometer", ex);
			return null;
		}

		return null;
	}

}

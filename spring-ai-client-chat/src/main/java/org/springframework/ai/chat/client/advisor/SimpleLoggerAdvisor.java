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

package org.springframework.ai.chat.client.advisor;

import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.lang.Nullable;

/**
 * A configurable logger advisor that logs the request and response messages at configurable log levels.
 *
 * @author Christian Tzolov
 * @author Engineer Zhong
 */
public class SimpleLoggerAdvisor implements CallAdvisor, StreamAdvisor {

	public static final Function<ChatClientRequest, String> DEFAULT_REQUEST_TO_STRING = ChatClientRequest::toString;
	public static final Function<ChatResponse, String> DEFAULT_RESPONSE_TO_STRING = ModelOptionsUtils::toJsonStringPrettyPrinter;

	private static final Logger logger = LoggerFactory.getLogger(SimpleLoggerAdvisor.class);

	private final Function<ChatClientRequest, String> requestToString;
	private final Function<ChatResponse, String> responseToString;
	private final int order;
	private final LogLevel requestLogLevel;
	private final LogLevel responseLogLevel;

	public SimpleLoggerAdvisor() {
		this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING, 0, LogLevel.DEBUG, LogLevel.DEBUG);
	}

	public SimpleLoggerAdvisor(int order) {
		this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING, order, LogLevel.DEBUG, LogLevel.DEBUG);
	}

	public SimpleLoggerAdvisor(@Nullable Function<ChatClientRequest, String> requestToString,
							   @Nullable Function<ChatResponse, String> responseToString,
							   int order,
							   LogLevel requestLogLevel,
							   LogLevel responseLogLevel) {
		this.requestToString = requestToString != null ? requestToString : DEFAULT_REQUEST_TO_STRING;
		this.responseToString = responseToString != null ? responseToString : DEFAULT_RESPONSE_TO_STRING;
		this.order = order;
		this.requestLogLevel = requestLogLevel;
		this.responseLogLevel = responseLogLevel;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		logRequest(chatClientRequest);

		ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

		logResponse(chatClientResponse);

		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
												 StreamAdvisorChain streamAdvisorChain) {
		logRequest(chatClientRequest);

		Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

		return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, this::logResponse);
	}

	private void logRequest(ChatClientRequest request) {
		logMessage(requestLogLevel, "request: {}", this.requestToString.apply(request));
	}

	private void logResponse(ChatClientResponse chatClientResponse) {
		logMessage(responseLogLevel, "response: {}", this.responseToString.apply(chatClientResponse.chatResponse()));
	}

	private void logMessage(LogLevel level, String format, Object arg) {
		switch (level) {
			case TRACE:
				if (logger.isTraceEnabled()) {
					logger.trace(format, arg);
				}
				break;
			case DEBUG:
				if (logger.isDebugEnabled()) {
					logger.debug(format, arg);
				}
				break;
			case INFO:
				if (logger.isInfoEnabled()) {
					logger.info(format, arg);
				}
				break;
			case WARN:
				if (logger.isWarnEnabled()) {
					logger.warn(format, arg);
				}
				break;
			case ERROR:
				if (logger.isErrorEnabled()) {
					logger.error(format, arg);
				}
				break;
		}
	}

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public String toString() {
		return SimpleLoggerAdvisor.class.getSimpleName();
	}

	public static Builder builder() {
		return new Builder();
	}

	public enum LogLevel {
		TRACE, DEBUG, INFO, WARN, ERROR
	}

	public static final class Builder {

		private Function<ChatClientRequest, String> requestToString;
		private Function<ChatResponse, String> responseToString;
		private int order = 0;
		private LogLevel requestLogLevel = LogLevel.DEBUG;
		private LogLevel responseLogLevel = LogLevel.DEBUG;

		private Builder() {
		}

		public Builder requestToString(Function<ChatClientRequest, String> requestToString) {
			this.requestToString = requestToString;
			return this;
		}

		public Builder responseToString(Function<ChatResponse, String> responseToString) {
			this.responseToString = responseToString;
			return this;
		}

		public Builder order(int order) {
			this.order = order;
			return this;
		}

		public Builder requestLogLevel(LogLevel logLevel) {
			this.requestLogLevel = logLevel;
			return this;
		}

		public Builder responseLogLevel(LogLevel logLevel) {
			this.responseLogLevel = logLevel;
			return this;
		}

		public Builder logLevel(LogLevel logLevel) {
			this.requestLogLevel = logLevel;
			this.responseLogLevel = logLevel;
			return this;
		}

		public SimpleLoggerAdvisor build() {
			return new SimpleLoggerAdvisor(
					this.requestToString,
					this.responseToString,
					this.order,
					this.requestLogLevel,
					this.responseLogLevel
			);
		}
	}
}

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

package org.springframework.ai.anthropic.http.okhttp;

/**
 * Callback for customizing the {@link SpringAiAnthropicHttpClient.Builder} used by
 * {@link org.springframework.ai.anthropic.AnthropicChatModel} before the underlying
 * {@code OkHttpClient} is built. Implement this interface to register OkHttp interceptors
 * (for example, a Spring Security OAuth2 client-credentials interceptor), swap the
 * dispatcher {@code ExecutorService}, or tweak any other OkHttp setting exposed by the
 * builder.
 *
 * @author Ilayaperumal Gopinathan
 * @since 2.0.0
 */
@FunctionalInterface
public interface AnthropicHttpClientBuilderCustomizer {

	/**
	 * Customize the {@link SpringAiAnthropicHttpClient.Builder} prior to building the
	 * underlying OkHttp client.
	 * <p>
	 * This method is called <em>twice</em> per
	 * {@link org.springframework.ai.anthropic.AnthropicChatModel} instance — once for the
	 * synchronous client and once for the asynchronous client. Implementations must be
	 * idempotent; side effects that must fire exactly once (for example, registering an
	 * {@code EventListenerFactory} or binding external state) should guard against
	 * re-execution.
	 * @param builder the builder to customize
	 */
	void customize(SpringAiAnthropicHttpClient.Builder builder);

}

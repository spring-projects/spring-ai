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

/**
 * Spring AI's customized OkHttp-backed implementation of the OpenAi Java SDK's
 * {@link com.openai.core.http.HttpClient} contract. Exists to wire Micrometer HTTP-layer
 * observability (span + metric + {@code traceparent} propagation) onto the underlying
 * OkHttp client, which the SDK's stock {@code OpenAiOkHttpClient.Builder} does not expose
 * a seam for. See the SDK's
 * <a href="https://github.com/openai/openai-java#custom-http-client">Custom HTTP
 * client</a> guide for the integration pattern.
 *
 * @since 2.0.0
 * @see org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient
 */
@NullMarked
package org.springframework.ai.openai.http.okhttp;

import org.jspecify.annotations.NullMarked;

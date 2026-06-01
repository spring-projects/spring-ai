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

package org.springframework.ai.chat.client.advisor.api;

/**
 * Marker interface for advisors that own the tool-call lifecycle. An advisor implementing
 * this interface takes responsibility for executing tools and driving the tool-call loop,
 * replacing the model-internal tool execution path.
 *
 * <p>
 * {@link org.springframework.ai.chat.client.DefaultChatClient} uses this marker to detect
 * whether a tool-call handling advisor is already present in the chain, and to avoid
 * auto-registering a duplicate when tools are configured on the {@code ChatClient}.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 * @see org.springframework.ai.chat.client.advisor.ToolCallAdvisor
 */
public interface ToolAdvisor extends Advisor {

}

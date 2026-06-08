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

package org.springframework.ai.chat.client;

import java.util.function.Consumer;

/**
 * Configuration options for the ChatClient request.
 *
 * Preset advisors parameters that can be passed as configuration options to the Advisor
 * context.
 *
 * @author Christian Tzolov
 */

public final class AdvisorParams {

	private AdvisorParams() {
	}

	public static final Consumer<ChatClient.AdvisorSpec> ENABLE_NATIVE_STRUCTURED_OUTPUT = a -> a
		.param(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey(), true);

	/**
	 * Controls whether a
	 * {@link org.springframework.ai.chat.client.advisor.ToolCallingAdvisor} is
	 * automatically added to the chain when tools are configured on the
	 * {@code ChatClient} and no explicit
	 * {@link org.springframework.ai.chat.client.advisor.api.ToolAdvisor} is already
	 * present. Auto-registration is enabled by default; pass {@code false} to opt out:
	 *
	 * <pre>{@code
	 * client.prompt()
	 *     .tools(myTool)
	 *     .advisors(AdvisorParams.toolCallingAdvisorAutoRegister(false))
	 *     .call();
	 * }</pre>
	 */
	public static Consumer<ChatClient.AdvisorSpec> toolCallingAdvisorAutoRegister(boolean enabled) {
		return a -> a.param(ChatClientAttributes.TOOL_CALLING_ADVISOR_AUTO_REGISTER.getKey(), enabled);
	}

	/**
	 * @deprecated since 2.0.0 in favor of
	 * {@link #toolCallingAdvisorAutoRegister(boolean)}.
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	public static Consumer<ChatClient.AdvisorSpec> toolCallAdvisorAutoRegister(boolean enabled) {
		return toolCallingAdvisorAutoRegister(enabled);
	}

}

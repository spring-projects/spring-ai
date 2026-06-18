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

package org.springframework.ai.chat.client.advisor;

import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;

/**
 * @author Christian Tzolov
 * @deprecated since 2.0.0 in favor of {@link ToolCallingAdvisor}. This is kept for
 * backward compatibility and will be removed in a future release.
 */
@Deprecated(since = "2.0.0", forRemoval = true)
public class ToolCallAdvisor extends ToolCallingAdvisor {

	protected ToolCallAdvisor(ToolCallingManager toolCallingManager,
			ToolExecutionEligibilityChecker toolExecutionEligibilityChecker, int advisorOrder,
			boolean conversationHistoryEnabled) {
		super(toolCallingManager, toolExecutionEligibilityChecker, advisorOrder, conversationHistoryEnabled,
				DEFAULT_MAX_IDENTICAL_TOOL_CALL_COUNT);
	}

	/**
	 * @deprecated since 2.0.0 in favor of {@link ToolCallingAdvisor#builder()}.
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	public static Builder<?> builder() {
		return new Builder<>();
	}

	/**
	 * @deprecated since 2.0.0 in favor of {@link ToolCallingAdvisor.Builder}.
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	public static class Builder<T extends Builder<T>> extends ToolCallingAdvisor.Builder<T> {

		@Deprecated(since = "2.0.0", forRemoval = true)
		protected Builder() {
		}

		@Deprecated(since = "2.0.0", forRemoval = true)
		@Override
		public ToolCallAdvisor build() {
			return new ToolCallAdvisor(getToolCallingManager(), getToolExecutionEligibilityChecker(), getAdvisorOrder(),
					isConversationHistoryEnabled());
		}

	}

}

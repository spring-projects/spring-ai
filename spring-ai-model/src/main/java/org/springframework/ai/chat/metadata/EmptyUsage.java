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

package org.springframework.ai.chat.metadata;

import java.util.Map;

/**
 * A EmptyUsage implementation that returns zero for all property getters
 *
 * @author John Blum
 * @author Ilayaperumal Gopinathan
 * @since 0.7.0
 */
public class EmptyUsage implements Usage {

	@Override
	public Integer getPromptTokens() {
		return 0;
	}

	@Override
	public Integer getCompletionTokens() {
		return 0;
	}

	@Override
	public Object getNativeUsage() {
		return Map.of();
	}

}

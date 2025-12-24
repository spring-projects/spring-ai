/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.tool.internal;

import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * This class bridges blocking Tools call and the reactive context. When calling tools, it
 * captures the context in a thread local, making it available to re-inject in a nested
 * reactive call.
 *
 * @author Daniel Garnier-Moiroux
 * @since 1.1.0
 */
public final class ToolCallReactiveContextHolder {

	private static final ThreadLocal<ContextView> context = ThreadLocal.withInitial(Context::empty);

	private ToolCallReactiveContextHolder() {
		// prevent instantiation
	}

	public static void setContext(ContextView contextView) {
		context.set(contextView);
	}

	public static ContextView getContext() {
		return context.get();
	}

	public static void clearContext() {
		context.remove();
	}

}

/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.model.function;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.util.Assert;

/**
 * Default implementation of {@link FunctionCallingOptions.Builder}.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 */
public class DefaultFunctionCallingOptionsBuilder
		extends DefaultChatOptionsBuilder<DefaultFunctionCallingOptionsBuilder>
		implements FunctionCallingOptions.Builder<DefaultFunctionCallingOptionsBuilder> {

	public DefaultFunctionCallingOptionsBuilder() {
		// Set the options in the parent class to be the same instance
		super(new DefaultFunctionCallingOptions());
	}

	public DefaultFunctionCallingOptionsBuilder functionCallbacks(List<FunctionCallback> functionCallbacks) {
		((FunctionCallingOptions) this.options).setFunctionCallbacks(functionCallbacks);
		return self();
	}

	public DefaultFunctionCallingOptionsBuilder functionCallbacks(FunctionCallback... functionCallbacks) {
		Assert.notNull(functionCallbacks, "FunctionCallbacks must not be null");
		((FunctionCallingOptions) this.options).setFunctionCallbacks(List.of(functionCallbacks));
		return self();
	}

	public DefaultFunctionCallingOptionsBuilder functions(Set<String> functions) {
		((FunctionCallingOptions) this.options).setFunctions(functions);
		return self();
	}

	public DefaultFunctionCallingOptionsBuilder function(String function) {
		Assert.notNull(function, "Function must not be null");
		var set = new HashSet<>(((FunctionCallingOptions) this.options).getFunctions());
		set.add(function);
		((FunctionCallingOptions) this.options).setFunctions(set);
		return self();
	}

	public DefaultFunctionCallingOptionsBuilder proxyToolCalls(Boolean proxyToolCalls) {
		((FunctionCallingOptions) this.options).setProxyToolCalls(proxyToolCalls);
		return self();
	}

	public DefaultFunctionCallingOptionsBuilder toolContext(Map<String, Object> context) {
		Assert.notNull(context, "Tool context must not be null");
		Map<String, Object> newContext = new HashMap<>(((FunctionCallingOptions) this.options).getToolContext());
		newContext.putAll(context);
		((FunctionCallingOptions) this.options).setToolContext(newContext);
		return self();
	}

	public DefaultFunctionCallingOptionsBuilder toolContext(String key, Object value) {
		Assert.notNull(key, "Key must not be null");
		Assert.notNull(value, "Value must not be null");
		Map<String, Object> newContext = new HashMap<>(((FunctionCallingOptions) this.options).getToolContext());
		newContext.put(key, value);
		((FunctionCallingOptions) this.options).setToolContext(newContext);
		return self();
	}

	public FunctionCallingOptions build() {
		return ((FunctionCallingOptions) this.options);
	}

}

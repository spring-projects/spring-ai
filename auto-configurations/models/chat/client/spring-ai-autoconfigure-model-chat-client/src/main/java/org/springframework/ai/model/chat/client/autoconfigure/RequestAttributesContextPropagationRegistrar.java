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

package org.springframework.ai.model.chat.client.autoconfigure;

import io.micrometer.context.ContextRegistry;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.context.request.RequestAttributesThreadLocalAccessor;

/**
 * Registers Spring Framework's {@link RequestAttributesThreadLocalAccessor} with the
 * global Micrometer {@link ContextRegistry} for the lifetime of the owning application
 * context, and removes it again when the context closes.
 * <p>
 * The {@link ContextRegistry} is a JVM-wide singleton, so this registrar only adds its
 * accessor when no other accessor with the same key is already registered, and only
 * removes it when it is still the accessor it registered itself - another component may
 * have replaced it in the meantime.
 *
 * @author luyu0425
 */
final class RequestAttributesContextPropagationRegistrar implements InitializingBean, DisposableBean {

	private final RequestAttributesThreadLocalAccessor accessor = new RequestAttributesThreadLocalAccessor();

	private boolean registered;

	@Override
	public void afterPropertiesSet() {
		ContextRegistry registry = ContextRegistry.getInstance();

		boolean alreadyRegistered = registry.getThreadLocalAccessors()
			.stream()
			.anyMatch(existing -> this.accessor.key().equals(existing.key()));

		if (!alreadyRegistered) {
			registry.registerThreadLocalAccessor(this.accessor);
			this.registered = true;
		}
	}

	@Override
	public void destroy() {
		if (!this.registered) {
			return;
		}

		ContextRegistry registry = ContextRegistry.getInstance();

		boolean stillOurs = registry.getThreadLocalAccessors().stream().anyMatch(existing -> existing == this.accessor);

		if (stillOurs) {
			registry.removeThreadLocalAccessor(this.accessor.key());
		}
	}

}

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

package org.springframework.ai.chat.cache.semantic;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.client.ChatClientRequest;

/**
 * Strategy that resolves the cache scope of a request handled by the
 * {@link SemanticCacheAdvisor}. Cached responses are only shared between requests that
 * resolve to the same scope, so the scope must identify the tenant, user or any other
 * boundary across which responses must not leak.
 *
 * <p>
 * The scope is typically derived from the authenticated principal or from a value the
 * application put into the {@link ChatClientRequest#context() request context}, for
 * example through {@code .advisors(a -> a.param("tenantId", tenantId))}. Applications
 * that deliberately share a single cache across all users can return a constant.
 * </p>
 *
 * <p>
 * The scope is the only isolation boundary: requests with different system prompts share
 * cached responses unless the resolver includes the system prompt in the scope, for
 * example {@code tenantId + "|" + request.prompt().getSystemMessage().getText()}.
 * </p>
 *
 * <p>
 * For streaming requests, the resolver may be invoked on a different thread than the one
 * that issued the request, so thread-bound state such as a security context holder backed
 * by a {@code ThreadLocal} may not be available. Prefer the request context in that case.
 * </p>
 *
 * @author Dimitar Proynov
 * @since 2.0.2
 */
@FunctionalInterface
public interface SemanticCacheScopeResolver {

	/**
	 * Resolves the cache scope of the given request.
	 * @param request the chat client request
	 * @return the cache scope, or {@code null} or a blank string to bypass the cache for
	 * this request
	 */
	@Nullable String resolveScope(ChatClientRequest request);

}

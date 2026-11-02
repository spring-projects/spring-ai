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

package org.springframework.ai.mcp.modern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema.CompleteReference;
import io.modelcontextprotocol.modern.McpSchema.CompleteRequest;
import io.modelcontextprotocol.modern.McpSchema.CompleteResult;
import io.modelcontextprotocol.modern.McpSchema.PromptReference;
import io.modelcontextprotocol.modern.McpSchema.ResourceReference;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpSyncCompletionRepository;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * An in-memory {@link McpSyncCompletionRepository} that dispatches
 * {@code completion/complete} to the {@link SyncCompletionRegistration} registered for
 * the request's prompt or resource reference.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class InMemorySyncCompletionRepository implements McpSyncCompletionRepository {

	private final Map<String, SyncCompletionRegistration> completions = new ConcurrentHashMap<>();

	private InMemorySyncCompletionRepository(Builder builder) {
		builder.completions.forEach(this::put);
	}

	public static Builder builder() {
		return new Builder();
	}

	public void add(SyncCompletionRegistration registration) {
		put(registration);
	}

	public boolean remove(CompleteReference reference) {
		Assert.notNull(reference, "reference must not be null");
		return this.completions.remove(key(reference)) != null;
	}

	@Override
	public CompleteResult complete(McpRequestContext ctx, CompleteRequest request) {
		SyncCompletionRegistration registration = this.completions.get(key(request.ref()));
		if (registration == null) {
			throw McpException.invalidParams("No completion handler for reference: " + describe(request.ref()));
		}
		return registration.handler().complete(ctx, request);
	}

	private void put(SyncCompletionRegistration registration) {
		Assert.notNull(registration, "registration must not be null");
		String key = key(registration.reference());
		if (this.completions.putIfAbsent(key, registration) != null) {
			throw new IllegalArgumentException("Completion for reference '" + key + "' already exists");
		}
	}

	private static String key(CompleteReference reference) {
		return reference.type() + ":" + describe(reference);
	}

	private static @Nullable String describe(CompleteReference reference) {
		if (reference instanceof PromptReference promptReference) {
			return promptReference.name();
		}
		if (reference instanceof ResourceReference resourceReference) {
			return resourceReference.uri();
		}
		return reference.toString();
	}

	/**
	 * Builds a {@link InMemorySyncCompletionRepository}.
	 */
	public static final class Builder {

		private final List<SyncCompletionRegistration> completions = new ArrayList<>();

		private Builder() {
		}

		public Builder completion(SyncCompletionRegistration registration) {
			Assert.notNull(registration, "registration must not be null");
			this.completions.add(registration);
			return this;
		}

		public Builder completions(List<SyncCompletionRegistration> registrations) {
			Assert.notNull(registrations, "registrations must not be null");
			this.completions.addAll(registrations);
			return this;
		}

		public InMemorySyncCompletionRepository build() {
			return new InMemorySyncCompletionRepository(this);
		}

	}

}

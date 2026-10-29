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
import java.util.function.BiPredicate;

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
 * {@code completion/complete} to the {@link SyncCompletionSpecification} registered for
 * the request's prompt or resource reference.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class InMemorySyncCompletionRepository implements McpSyncCompletionRepository {

	private final Map<String, SyncCompletionSpecification> completions = new ConcurrentHashMap<>();

	private final @Nullable BiPredicate<McpRequestContext, CompleteReference> referenceGuard;

	private InMemorySyncCompletionRepository(Builder builder) {
		this.referenceGuard = builder.referenceGuard;
		builder.completions.forEach(this::put);
	}

	public static Builder builder() {
		return new Builder();
	}

	public void add(SyncCompletionSpecification specification) {
		put(specification);
	}

	public boolean remove(CompleteReference reference) {
		Assert.notNull(reference, "reference must not be null");
		return this.completions.remove(key(reference)) != null;
	}

	public List<CompleteReference> references() {
		return this.completions.values().stream().map(SyncCompletionSpecification::reference).toList();
	}

	@Override
	public CompleteResult complete(McpRequestContext ctx, CompleteRequest request) {
		SyncCompletionSpecification spec = this.completions.get(key(request.ref()));
		if (spec == null || (this.referenceGuard != null && !this.referenceGuard.test(ctx, request.ref()))) {
			// A reference the caller may not see answers exactly like an unknown one.
			throw McpException.invalidParams("No completion handler for reference: " + describe(request.ref()));
		}
		return spec.handler().complete(ctx, request);
	}

	private void put(SyncCompletionSpecification specification) {
		Assert.notNull(specification, "specification must not be null");
		String key = key(specification.reference());
		if (this.completions.putIfAbsent(key, specification) != null) {
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

		private final List<SyncCompletionSpecification> completions = new ArrayList<>();

		private @Nullable BiPredicate<McpRequestContext, CompleteReference> referenceGuard;

		private Builder() {
		}

		/**
		 * Decides per request whether the caller may complete the given reference, so
		 * that a prompt or resource template hidden by a {@link McpVisibilityFilter}
		 * cannot be discovered through {@code completion/complete}. A rejected reference
		 * answers like an unknown one. Unset, every reference with a handler is served.
		 */
		public Builder referenceGuard(@Nullable BiPredicate<McpRequestContext, CompleteReference> referenceGuard) {
			this.referenceGuard = referenceGuard;
			return this;
		}

		public Builder completion(SyncCompletionSpecification specification) {
			Assert.notNull(specification, "specification must not be null");
			this.completions.add(specification);
			return this;
		}

		public Builder completions(List<SyncCompletionSpecification> specifications) {
			Assert.notNull(specifications, "specifications must not be null");
			this.completions.addAll(specifications);
			return this;
		}

		public InMemorySyncCompletionRepository build() {
			return new InMemorySyncCompletionRepository(this);
		}

	}

}

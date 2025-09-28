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

package org.springframework.ai.google.genai.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.genai.AsyncCaches;
import com.google.genai.Caches;
import com.google.genai.Client;
import com.google.genai.Pager;
import com.google.genai.types.CachedContent;
import com.google.genai.types.CreateCachedContentConfig;
import com.google.genai.types.DeleteCachedContentConfig;
import com.google.genai.types.DeleteCachedContentResponse;
import com.google.genai.types.GetCachedContentConfig;
import com.google.genai.types.ListCachedContentsConfig;
import com.google.genai.types.UpdateCachedContentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Service for managing cached content in Google GenAI. Provides synchronous and
 * asynchronous operations for creating, retrieving, updating, and deleting cached
 * content.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public class GoogleGenAiCachedContentService {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiCachedContentService.class);

	private final Client genAiClient;

	private final Caches caches;

	private final AsyncCaches asyncCaches;

	public GoogleGenAiCachedContentService(Client genAiClient) {
		Assert.notNull(genAiClient, "GenAI client must not be null");
		// The caller should ensure these are not null before creating the service
		this.genAiClient = genAiClient;
		this.caches = genAiClient.caches;
		this.asyncCaches = genAiClient.async.caches;
	}

	// Synchronous Operations

	/**
	 * Creates cached content from the given request.
	 * @param request the cached content creation request
	 * @return the created cached content
	 */
	public GoogleGenAiCachedContent create(CachedContentRequest request) {
		Assert.notNull(request, "Request must not be null");

		CreateCachedContentConfig.Builder configBuilder = CreateCachedContentConfig.builder()
			.contents(request.getContents());

		if (request.getSystemInstruction() != null) {
			configBuilder.systemInstruction(request.getSystemInstruction());
		}

		if (request.getDisplayName() != null) {
			configBuilder.displayName(request.getDisplayName());
		}

		if (request.getTtl() != null) {
			configBuilder.ttl(request.getTtl());
		}
		else if (request.getExpireTime() != null) {
			configBuilder.expireTime(request.getExpireTime());
		}

		try {
			CreateCachedContentConfig config = configBuilder.build();
			CachedContent cachedContent = this.caches.create(request.getModel(), config);
			logger.debug("Created cached content: {}", cachedContent.name().orElse("unknown"));
			return GoogleGenAiCachedContent.from(cachedContent);
		}
		catch (Exception e) {
			logger.error("Failed to create cached content", e);
			throw new CachedContentException("Failed to create cached content", e);
		}
	}

	/**
	 * Retrieves cached content by name.
	 * @param name the cached content name
	 * @return the cached content, or null if not found
	 */
	@Nullable
	public GoogleGenAiCachedContent get(String name) {
		Assert.hasText(name, "Name must not be empty");

		try {
			GetCachedContentConfig config = GetCachedContentConfig.builder().build();
			CachedContent cachedContent = this.caches.get(name, config);
			logger.debug("Retrieved cached content: {}", name);
			return GoogleGenAiCachedContent.from(cachedContent);
		}
		catch (Exception e) {
			logger.error("Failed to get cached content: {}", name, e);
			return null;
		}
	}

	/**
	 * Updates cached content with new TTL or expiration.
	 * @param name the cached content name
	 * @param request the update request
	 * @return the updated cached content
	 */
	public GoogleGenAiCachedContent update(String name, CachedContentUpdateRequest request) {
		Assert.hasText(name, "Name must not be empty");
		Assert.notNull(request, "Request must not be null");

		UpdateCachedContentConfig.Builder configBuilder = UpdateCachedContentConfig.builder();

		if (request.getTtl() != null) {
			configBuilder.ttl(request.getTtl());
		}

		if (request.getExpireTime() != null) {
			configBuilder.expireTime(request.getExpireTime());
		}

		try {
			UpdateCachedContentConfig config = configBuilder.build();
			CachedContent cachedContent = this.caches.update(name, config);
			logger.debug("Updated cached content: {}", name);
			return GoogleGenAiCachedContent.from(cachedContent);
		}
		catch (Exception e) {
			logger.error("Failed to update cached content: {}", name, e);
			throw new CachedContentException("Failed to update cached content: " + name, e);
		}
	}

	/**
	 * Deletes cached content by name.
	 * @param name the cached content name
	 * @return true if deleted successfully, false otherwise
	 */
	public boolean delete(String name) {
		Assert.hasText(name, "Name must not be empty");

		try {
			DeleteCachedContentConfig config = DeleteCachedContentConfig.builder().build();
			DeleteCachedContentResponse response = this.caches.delete(name, config);
			logger.debug("Deleted cached content: {}", name);
			return true;
		}
		catch (Exception e) {
			logger.error("Failed to delete cached content: {}", name, e);
			return false;
		}
	}

	/**
	 * Lists all cached content with optional pagination.
	 * @param pageSize the page size (null for default)
	 * @param pageToken the page token for pagination (null for first page)
	 * @return list of cached content
	 */
	public CachedContentPage list(@Nullable Integer pageSize, @Nullable String pageToken) {
		ListCachedContentsConfig.Builder configBuilder = ListCachedContentsConfig.builder();

		if (pageSize != null && pageSize > 0) {
			configBuilder.pageSize(pageSize);
		}

		if (pageToken != null) {
			configBuilder.pageToken(pageToken);
		}

		try {
			ListCachedContentsConfig config = configBuilder.build();
			Pager<CachedContent> pager = this.caches.list(config);

			List<GoogleGenAiCachedContent> contents = new ArrayList<>();
			// Iterate through the first page of results
			for (CachedContent content : pager) {
				contents.add(GoogleGenAiCachedContent.from(content));
				// Only get the first page worth of results
				if (contents.size() >= (pageSize != null ? pageSize : 100)) {
					break;
				}
			}

			// Note: Pager doesn't expose page tokens directly, so we can't support
			// pagination
			// in the same way. This is a limitation of the SDK.
			logger.debug("Listed {} cached content items", contents.size());

			return new CachedContentPage(contents, null);
		}
		catch (Exception e) {
			logger.error("Failed to list cached content", e);
			throw new CachedContentException("Failed to list cached content", e);
		}
	}

	/**
	 * Lists all cached content without pagination.
	 * @return list of all cached content
	 */
	public List<GoogleGenAiCachedContent> listAll() {
		List<GoogleGenAiCachedContent> allContent = new ArrayList<>();
		String pageToken = null;

		do {
			CachedContentPage page = list(100, pageToken);
			allContent.addAll(page.getContents());
			pageToken = page.getNextPageToken();
		}
		while (pageToken != null);

		return allContent;
	}

	// Asynchronous Operations

	/**
	 * Asynchronously creates cached content from the given request.
	 * @param request the cached content creation request
	 * @return a future containing the created cached content
	 */
	public CompletableFuture<GoogleGenAiCachedContent> createAsync(CachedContentRequest request) {
		Assert.notNull(request, "Request must not be null");

		CreateCachedContentConfig.Builder configBuilder = CreateCachedContentConfig.builder()
			.contents(request.getContents());

		if (request.getSystemInstruction() != null) {
			configBuilder.systemInstruction(request.getSystemInstruction());
		}

		if (request.getDisplayName() != null) {
			configBuilder.displayName(request.getDisplayName());
		}

		if (request.getTtl() != null) {
			configBuilder.ttl(request.getTtl());
		}
		else if (request.getExpireTime() != null) {
			configBuilder.expireTime(request.getExpireTime());
		}

		try {
			CreateCachedContentConfig config = configBuilder.build();
			return this.asyncCaches.create(request.getModel(), config).thenApply(GoogleGenAiCachedContent::from);
		}
		catch (Exception e) {
			logger.error("Failed to create cached content asynchronously", e);
			return CompletableFuture.failedFuture(new CachedContentException("Failed to create cached content", e));
		}
	}

	/**
	 * Asynchronously retrieves cached content by name.
	 * @param name the cached content name
	 * @return a future containing the cached content
	 */
	public CompletableFuture<GoogleGenAiCachedContent> getAsync(String name) {
		Assert.hasText(name, "Name must not be empty");

		try {
			GetCachedContentConfig config = GetCachedContentConfig.builder().build();
			return this.asyncCaches.get(name, config).thenApply(GoogleGenAiCachedContent::from);
		}
		catch (Exception e) {
			logger.error("Failed to get cached content asynchronously: {}", name, e);
			return CompletableFuture.failedFuture(new CachedContentException("Failed to get cached content", e));
		}
	}

	/**
	 * Asynchronously updates cached content with new TTL or expiration.
	 * @param name the cached content name
	 * @param request the update request
	 * @return a future containing the updated cached content
	 */
	public CompletableFuture<GoogleGenAiCachedContent> updateAsync(String name, CachedContentUpdateRequest request) {
		Assert.hasText(name, "Name must not be empty");
		Assert.notNull(request, "Request must not be null");

		UpdateCachedContentConfig.Builder configBuilder = UpdateCachedContentConfig.builder();

		if (request.getTtl() != null) {
			configBuilder.ttl(request.getTtl());
		}

		if (request.getExpireTime() != null) {
			configBuilder.expireTime(request.getExpireTime());
		}

		try {
			UpdateCachedContentConfig config = configBuilder.build();
			return this.asyncCaches.update(name, config).thenApply(GoogleGenAiCachedContent::from);
		}
		catch (Exception e) {
			logger.error("Failed to update cached content asynchronously: {}", name, e);
			return CompletableFuture.failedFuture(new CachedContentException("Failed to update cached content", e));
		}
	}

	/**
	 * Asynchronously deletes cached content by name.
	 * @param name the cached content name
	 * @return a future indicating success
	 */
	public CompletableFuture<Boolean> deleteAsync(String name) {
		Assert.hasText(name, "Name must not be empty");

		try {
			DeleteCachedContentConfig config = DeleteCachedContentConfig.builder().build();
			return this.asyncCaches.delete(name, config).thenApply(response -> true).exceptionally(e -> {
				logger.error("Failed to delete cached content asynchronously: {}", name, e);
				return false;
			});
		}
		catch (Exception e) {
			logger.error("Failed to delete cached content asynchronously: {}", name, e);
			return CompletableFuture.completedFuture(false);
		}
	}

	// Utility methods

	/**
	 * Extends the TTL of cached content by the specified duration.
	 * @param name the cached content name
	 * @param additionalTtl the additional TTL to add
	 * @return the updated cached content
	 */
	public GoogleGenAiCachedContent extendTtl(String name, Duration additionalTtl) {
		Assert.hasText(name, "Name must not be empty");
		Assert.notNull(additionalTtl, "Additional TTL must not be null");

		GoogleGenAiCachedContent existing = get(name);
		if (existing == null) {
			throw new CachedContentException("Cached content not found: " + name);
		}

		Instant newExpireTime = existing.getExpireTime() != null ? existing.getExpireTime().plus(additionalTtl)
				: Instant.now().plus(additionalTtl);

		CachedContentUpdateRequest updateRequest = CachedContentUpdateRequest.builder()
			.expireTime(newExpireTime)
			.build();

		return update(name, updateRequest);
	}

	/**
	 * Refreshes the expiration of cached content to the maximum TTL.
	 * @param name the cached content name
	 * @param maxTtl the maximum TTL to set
	 * @return the updated cached content
	 */
	public GoogleGenAiCachedContent refreshExpiration(String name, Duration maxTtl) {
		Assert.hasText(name, "Name must not be empty");
		Assert.notNull(maxTtl, "Max TTL must not be null");

		CachedContentUpdateRequest updateRequest = CachedContentUpdateRequest.builder().ttl(maxTtl).build();

		return update(name, updateRequest);
	}

	/**
	 * Removes all expired cached content.
	 * @return the number of expired items removed
	 */
	public int cleanupExpired() {
		List<GoogleGenAiCachedContent> allContent = listAll();
		int removed = 0;

		for (GoogleGenAiCachedContent content : allContent) {
			if (content.isExpired()) {
				if (delete(content.getName())) {
					removed++;
					logger.info("Removed expired cached content: {}", content.getName());
				}
			}
		}

		return removed;
	}

	/**
	 * Result of listing cached content with pagination support.
	 */
	public static class CachedContentPage {

		private final List<GoogleGenAiCachedContent> contents;

		private final String nextPageToken;

		public CachedContentPage(List<GoogleGenAiCachedContent> contents, String nextPageToken) {
			this.contents = contents != null ? new ArrayList<>(contents) : new ArrayList<>();
			this.nextPageToken = nextPageToken;
		}

		public List<GoogleGenAiCachedContent> getContents() {
			return this.contents;
		}

		public String getNextPageToken() {
			return this.nextPageToken;
		}

		public boolean hasNextPage() {
			return this.nextPageToken != null;
		}

	}

	/**
	 * Exception thrown when cached content operations fail.
	 */
	public static class CachedContentException extends RuntimeException {

		public CachedContentException(String message) {
			super(message);
		}

		public CachedContentException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}

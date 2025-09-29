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

package org.springframework.ai.google.genai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.google.genai.Client;

import org.springframework.ai.google.genai.cache.CachedContentRequest;
import org.springframework.ai.google.genai.cache.CachedContentUpdateRequest;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContent;
import org.springframework.ai.google.genai.cache.GoogleGenAiCachedContentService;

/**
 * Test implementation that mimics GoogleGenAiCachedContentService but uses in-memory
 * storage instead of actual API calls. Used for testing chat model integration with
 * cached content.
 *
 * Note: This class does NOT extend GoogleGenAiCachedContentService to avoid dependencies
 * on the Client's internal structure.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public class TestGoogleGenAiCachedContentService {

	private final Map<String, GoogleGenAiCachedContent> cache = new HashMap<>();

	private int nextId = 1;

	public TestGoogleGenAiCachedContentService() {
		// No-op constructor for testing
	}

	public TestGoogleGenAiCachedContentService(Client genAiClient) {
		// Ignore the client for testing purposes
	}

	public GoogleGenAiCachedContent create(CachedContentRequest request) {
		String name = "cachedContent/" + (this.nextId++);
		GoogleGenAiCachedContent cached = GoogleGenAiCachedContent.builder()
			.name(name)
			.model(request.getModel())
			.displayName(request.getDisplayName())
			.ttl(request.getTtl())
			.expireTime(request.getExpireTime())
			.contents(request.getContents())
			.systemInstruction(request.getSystemInstruction())
			.createTime(java.time.Instant.now())
			.build();

		this.cache.put(name, cached);
		return cached;
	}

	public GoogleGenAiCachedContent get(String name) {
		return this.cache.get(name);
	}

	public GoogleGenAiCachedContent update(String name, CachedContentUpdateRequest request) {
		GoogleGenAiCachedContent existing = this.cache.get(name);
		if (existing == null) {
			throw new CachedContentException("Cached content not found: " + name);
		}

		GoogleGenAiCachedContent updated = GoogleGenAiCachedContent.builder()
			.name(name)
			.model(existing.getModel())
			.displayName(existing.getDisplayName())
			.ttl(request.getTtl() != null ? request.getTtl() : existing.getTtl())
			.expireTime(request.getExpireTime() != null ? request.getExpireTime() : existing.getExpireTime())
			.contents(existing.getContents())
			.systemInstruction(existing.getSystemInstruction())
			.createTime(existing.getCreateTime())
			.updateTime(java.time.Instant.now())
			.build();

		this.cache.put(name, updated);
		return updated;
	}

	public boolean delete(String name) {
		return this.cache.remove(name) != null;
	}

	public GoogleGenAiCachedContentService.CachedContentPage list(Integer pageSize, String pageToken) {
		List<GoogleGenAiCachedContent> contents = new ArrayList<>(this.cache.values());
		return new GoogleGenAiCachedContentService.CachedContentPage(contents, null);
	}

	public List<GoogleGenAiCachedContent> listAll() {
		return new ArrayList<>(this.cache.values());
	}

	public CompletableFuture<GoogleGenAiCachedContent> createAsync(CachedContentRequest request) {
		return CompletableFuture.completedFuture(create(request));
	}

	public CompletableFuture<GoogleGenAiCachedContent> getAsync(String name) {
		return CompletableFuture.completedFuture(get(name));
	}

	public CompletableFuture<GoogleGenAiCachedContent> updateAsync(String name, CachedContentUpdateRequest request) {
		return CompletableFuture.completedFuture(update(name, request));
	}

	public CompletableFuture<Boolean> deleteAsync(String name) {
		return CompletableFuture.completedFuture(delete(name));
	}

	public GoogleGenAiCachedContent extendTtl(String name, Duration additionalTtl) {
		GoogleGenAiCachedContent existing = get(name);
		if (existing == null) {
			throw new CachedContentException("Cached content not found: " + name);
		}

		java.time.Instant newExpireTime = existing.getExpireTime() != null
				? existing.getExpireTime().plus(additionalTtl) : java.time.Instant.now().plus(additionalTtl);

		CachedContentUpdateRequest updateRequest = CachedContentUpdateRequest.builder()
			.expireTime(newExpireTime)
			.build();

		return update(name, updateRequest);
	}

	public GoogleGenAiCachedContent refreshExpiration(String name, Duration maxTtl) {
		CachedContentUpdateRequest updateRequest = CachedContentUpdateRequest.builder().ttl(maxTtl).build();
		return update(name, updateRequest);
	}

	public int cleanupExpired() {
		List<String> toRemove = new ArrayList<>();
		for (Map.Entry<String, GoogleGenAiCachedContent> entry : this.cache.entrySet()) {
			if (entry.getValue().isExpired()) {
				toRemove.add(entry.getKey());
			}
		}
		toRemove.forEach(this.cache::remove);
		return toRemove.size();
	}

	/**
	 * Test method to clear all cached content.
	 */
	public void clearAll() {
		this.cache.clear();
	}

	/**
	 * Test method to check if cache contains a specific item.
	 * @param name the cached content name
	 * @return true if the cache contains the item
	 */
	public boolean contains(String name) {
		return this.cache.containsKey(name);
	}

	/**
	 * Test method to get the current cache size.
	 * @return the number of cached items
	 */
	public int size() {
		return this.cache.size();
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

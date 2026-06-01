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

package org.springframework.ai.chat.client.advisor.tool.search.eviction;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.advisor.tool.search.api.ToolIndexEvictionStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ToolIndexEvictionStrategy} implementations:
 * {@link NeverEvictStrategy}, {@link LruEvictionStrategy}, {@link TtlEvictionStrategy},
 * and {@link CompositeEvictionStrategy}.
 *
 * @author Christian Tzolov
 */
class ToolIndexEvictionStrategyTests {

	// -------------------------------------------------------------------------
	// AlwaysEvictStrategy
	// -------------------------------------------------------------------------

	@Test
	void alwaysEvict_onAccess_returnsCurrentSession() {
		AlwaysEvictStrategy strategy = AlwaysEvictStrategy.INSTANCE;

		assertThat(strategy.onAccess("session-1")).containsExactly("session-1");
		assertThat(strategy.onAccess("session-2")).containsExactly("session-2");
		assertThat(strategy.onAccess("session-1")).containsExactly("session-1");
	}

	@Test
	void alwaysEvict_onRemoved_doesNothing() {
		// Must not throw.
		AlwaysEvictStrategy.INSTANCE.onRemoved("any-session");
	}

	@Test
	void alwaysEvict_composedWithLru_stillEvictsCurrentSession() {
		// AlwaysEvict dominates: current session is always evicted regardless of LRU
		// state.
		AlwaysEvictStrategy always = AlwaysEvictStrategy.INSTANCE;
		LruEvictionStrategy lru = new LruEvictionStrategy(10);
		CompositeEvictionStrategy composite = new CompositeEvictionStrategy(always, lru);

		Set<String> evicted = composite.onAccess("a");
		assertThat(evicted).contains("a");
	}

	// -------------------------------------------------------------------------
	// NeverEvictStrategy
	// -------------------------------------------------------------------------

	@Test
	void neverEvict_onAccess_alwaysReturnsEmpty() {
		NeverEvictStrategy strategy = NeverEvictStrategy.INSTANCE;

		assertThat(strategy.onAccess("session-1")).isEmpty();
		assertThat(strategy.onAccess("session-2")).isEmpty();
		assertThat(strategy.onAccess("session-1")).isEmpty();
	}

	@Test
	void neverEvict_onRemoved_doesNothing() {
		// Must not throw.
		NeverEvictStrategy.INSTANCE.onRemoved("any-session");
	}

	// -------------------------------------------------------------------------
	// LruEvictionStrategy
	// -------------------------------------------------------------------------

	@Test
	void lru_belowCapacity_noEviction() {
		LruEvictionStrategy strategy = new LruEvictionStrategy(3);

		assertThat(strategy.onAccess("a")).isEmpty();
		assertThat(strategy.onAccess("b")).isEmpty();
		assertThat(strategy.onAccess("c")).isEmpty();
	}

	@Test
	void lru_capacityExceeded_evictsLruSession() {
		LruEvictionStrategy strategy = new LruEvictionStrategy(2);

		strategy.onAccess("a");
		strategy.onAccess("b");

		// "a" is LRU — accessing "c" should evict "a"
		Set<String> evicted = strategy.onAccess("c");
		assertThat(evicted).containsExactly("a");
	}

	@Test
	void lru_recentAccessUpdatesOrder() {
		LruEvictionStrategy strategy = new LruEvictionStrategy(2);

		strategy.onAccess("a");
		strategy.onAccess("b");
		// Re-access "a" — it becomes MRU, so "b" is now LRU
		strategy.onAccess("a");

		Set<String> evicted = strategy.onAccess("c");
		assertThat(evicted).containsExactly("b");
	}

	@Test
	void lru_sameSessionAccess_doesNotEvict() {
		LruEvictionStrategy strategy = new LruEvictionStrategy(1);

		strategy.onAccess("a");

		// Accessing the same session again should not evict it
		Set<String> evicted = strategy.onAccess("a");
		assertThat(evicted).isEmpty();
	}

	@Test
	void lru_onRemoved_preventsEvictionOfAlreadyClearedSession() {
		LruEvictionStrategy strategy = new LruEvictionStrategy(2);

		strategy.onAccess("a");
		strategy.onAccess("b");
		strategy.onRemoved("a"); // externally removed — strategy should forget "a"

		// Now only "b" is tracked; adding "c" should not evict "a" again
		Set<String> evicted = strategy.onAccess("c");
		assertThat(evicted).doesNotContain("a");
	}

	@Test
	void lru_maxSessionsMustBePositive() {
		assertThatThrownBy(() -> new LruEvictionStrategy(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new LruEvictionStrategy(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	// -------------------------------------------------------------------------
	// TtlEvictionStrategy
	// -------------------------------------------------------------------------

	@Test
	void ttl_freshSession_notEvicted() {
		TtlEvictionStrategy strategy = new TtlEvictionStrategy(Duration.ofMinutes(10));

		strategy.onAccess("a");

		// Accessing immediately — "a" should not be expired
		Set<String> evicted = strategy.onAccess("b");
		assertThat(evicted).doesNotContain("a");
	}

	@Test
	void ttl_expiredSession_evictedOnNextAccess() throws InterruptedException {
		// Very short TTL so the test doesn't need to sleep long
		TtlEvictionStrategy strategy = new TtlEvictionStrategy(Duration.ofMillis(50));

		strategy.onAccess("a");
		Thread.sleep(60); // wait for "a" to expire

		Set<String> evicted = strategy.onAccess("b");
		assertThat(evicted).containsExactly("a");
	}

	@Test
	void ttl_currentSessionNotEvictedEvenIfIdle() throws InterruptedException {
		TtlEvictionStrategy strategy = new TtlEvictionStrategy(Duration.ofMillis(50));

		strategy.onAccess("a");
		Thread.sleep(60);

		// "a" itself is being accessed — it must never appear in the eviction set
		Set<String> evicted = strategy.onAccess("a");
		assertThat(evicted).doesNotContain("a");
	}

	@Test
	void ttl_onRemoved_removesSessionFromTracking() throws InterruptedException {
		TtlEvictionStrategy strategy = new TtlEvictionStrategy(Duration.ofMillis(50));

		strategy.onAccess("a");
		strategy.onRemoved("a");
		Thread.sleep(60);

		// "a" was removed from tracking — no eviction expected
		Set<String> evicted = strategy.onAccess("b");
		assertThat(evicted).doesNotContain("a");
	}

	@Test
	void ttl_mustBePositive() {
		assertThatThrownBy(() -> new TtlEvictionStrategy(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TtlEvictionStrategy(Duration.ofSeconds(-1)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	// -------------------------------------------------------------------------
	// CompositeEvictionStrategy
	// -------------------------------------------------------------------------

	@Test
	void composite_unionsEvictionDecisions() {
		// LRU(1) evicts "a" when "b" accesses; TTL won't expire yet — union still has "a"
		LruEvictionStrategy lru = new LruEvictionStrategy(1);
		TtlEvictionStrategy ttl = new TtlEvictionStrategy(Duration.ofMinutes(10));
		CompositeEvictionStrategy composite = new CompositeEvictionStrategy(lru, ttl);

		composite.onAccess("a");
		Set<String> evicted = composite.onAccess("b");

		assertThat(evicted).containsExactly("a");
	}

	@Test
	void composite_bothStrategiesReceiveOnRemoved() throws InterruptedException {
		LruEvictionStrategy lru = new LruEvictionStrategy(2);
		TtlEvictionStrategy ttl = new TtlEvictionStrategy(Duration.ofMillis(50));
		CompositeEvictionStrategy composite = new CompositeEvictionStrategy(lru, ttl);

		composite.onAccess("a");
		composite.onRemoved("a");
		Thread.sleep(60);

		// Neither LRU nor TTL should report "a" after onRemoved
		Set<String> evicted = composite.onAccess("b");
		assertThat(evicted).doesNotContain("a");
	}

	@Test
	void composite_requiresAtLeastOneDelegate() {
		assertThatThrownBy(() -> new CompositeEvictionStrategy()).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void composite_singleDelegate_behavesLikeDelegate() {
		NeverEvictStrategy never = NeverEvictStrategy.INSTANCE;
		CompositeEvictionStrategy composite = new CompositeEvictionStrategy(never);

		assertThat(composite.onAccess("x")).isEmpty();
		assertThat(composite.onAccess("y")).isEmpty();
	}

}

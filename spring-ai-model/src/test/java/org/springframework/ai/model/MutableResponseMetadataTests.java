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

package org.springframework.ai.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link MutableResponseMetadata}.
 *
 * @author Abu Hena Mostafa Kamal
 */

class MutableResponseMetadataTests {

	private MutableResponseMetadata metadata;

	@BeforeEach
	void setUp() {
		this.metadata = new MutableResponseMetadata();
	}

	@Test
	void isEmptyWhenNewlyCreated() {
		assertThat(this.metadata.isEmpty()).isTrue();
	}

	@Test
	void isNotEmptyAfterPut() {
		this.metadata.put("key", "value");
		assertThat(this.metadata.isEmpty()).isFalse();
	}

	@Test
	void putAndGetReturnsStoredValue() {
		this.metadata.put("model", "gpt-4o");
		assertThat(this.metadata.<String>get("model")).isEqualTo("gpt-4o");
	}

	@Test
	void putAndGetReturnsStoredValueAsInteger() {
		this.metadata.put("timeout", 30);
		assertThat(this.metadata.<Integer>get("timeout")).isEqualTo(30);
	}

	@Test
	void getReturnsNullForMissingKey() {
		assertThat(this.metadata.<String>get("missing")).isNull();
	}

	@Test
	void putSupportsChainingAndStoresMultipleEntries() {
		this.metadata.put("k1", "v1").put("k2", "v2").put("k3", "v3");
		assertThat(this.metadata.<String>get("k1")).isEqualTo("v1");
		assertThat(this.metadata.<String>get("k2")).isEqualTo("v2");
		assertThat(this.metadata.<String>get("k3")).isEqualTo("v3");
	}

	@Test
	void getRequiredReturnsValueWhenPresent() {
		this.metadata.put("requestId", "abc-123");
		assertThat(this.metadata.<String>getRequired("requestId")).isEqualTo("abc-123");
	}

	@Test
	void getRequiredThrowsIllegalArgumentExceptionWhenKeyMissing() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.metadata.getRequired("nonexistent"))
			.withMessageContaining("nonexistent");
	}

	@Test
	void containsKeyReturnsTrueWhenKeyPresent() {
		this.metadata.put("foo", "bar");
		assertThat(this.metadata.containsKey("foo")).isTrue();
	}

	@Test
	void containsKeyReturnsFalseWhenKeyAbsent() {
		assertThat(this.metadata.containsKey("foo")).isFalse();
	}

	@Test
	void getOrDefaultReturnsValueWhenKeyPresent() {
		this.metadata.put("timeout", 30);
		assertThat(this.metadata.<Integer>getOrDefault("timeout", 60)).isEqualTo(30);
	}

	@Test
	void getOrDefaultReturnsDefaultWhenKeyAbsent() {
		assertThat(this.metadata.<Integer>getOrDefault("timeout", 60)).isEqualTo(60);
	}

	@Test
	void removeDeletesEntryAndReturnsPreviousValue() {
		this.metadata.put("temp", "delete-me");
		Object removed = this.metadata.remove("temp");
		assertThat(removed).isEqualTo("delete-me");
		assertThat(this.metadata.containsKey("temp")).isFalse();
	}

	@Test
	void removeReturnsNullForNonExistentKey() {
		assertThat(this.metadata.remove("nonexistent")).isNull();
	}

	@Test
	void clearRemovesAllEntries() {
		this.metadata.put("a", 1).put("b", 2).put("c", 3);
		this.metadata.clear();
		assertThat(this.metadata.isEmpty()).isTrue();
	}

	@Test
	void keySetReturnsAllKeys() {
		this.metadata.put("x", 1).put("y", 2);
		assertThat(this.metadata.keySet()).containsExactlyInAnyOrder("x", "y");
	}

	@Test
	void keySetIsUnmodifiable() {
		this.metadata.put("key", "value");
		assertThat(this.metadata.keySet()).isUnmodifiable();
	}

	@Test
	void entrySetReturnsAllEntries() {
		this.metadata.put("model", "claude").put("version", "3");
		assertThat(this.metadata.entrySet()).hasSize(2);
		assertThat(this.metadata.entrySet()).anyMatch(e -> e.getKey().equals("model") && e.getValue().equals("claude"));
	}

	@Test
	void entrySetIsUnmodifiable() {
		this.metadata.put("key", "value");
		assertThat(this.metadata.entrySet()).isUnmodifiable();
	}

	@Test
	void computeIfAbsentInsertsAndReturnsComputedValue() {
		String result = this.metadata.computeIfAbsent("generated", k -> "computed-" + k);
		assertThat(result).isEqualTo("computed-generated");
		assertThat(this.metadata.<String>get("generated")).isEqualTo("computed-generated");
	}

	@Test
	void computeIfAbsentDoesNotOverwriteExistingValue() {
		this.metadata.put("existing", "original");
		String result = this.metadata.computeIfAbsent("existing", k -> "should-not-replace");
		assertThat(result).isEqualTo("original");
		assertThat(this.metadata.<String>get("existing")).isEqualTo("original");
	}

	@Test
	void getRawMapReflectsCurrentState() {
		this.metadata.put("a", 1).put("b", 2);
		assertThat(this.metadata.getRawMap()).containsEntry("a", 1).containsEntry("b", 2);
	}

	@Test
	void putOverwritesExistingValue() {
		this.metadata.put("key", "original");
		this.metadata.put("key", "updated");
		assertThat(this.metadata.<String>get("key")).isEqualTo("updated");
	}

}

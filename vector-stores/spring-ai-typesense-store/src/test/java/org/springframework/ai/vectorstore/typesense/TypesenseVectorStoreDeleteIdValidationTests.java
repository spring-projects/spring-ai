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

package org.springframework.ai.vectorstore.typesense;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.typesense.api.Client;
import org.typesense.api.Collection;
import org.typesense.api.Documents;

import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TypesenseVectorStore#validateIdForFilter(String)} and for the
 * wiring between {@link TypesenseVectorStore#doDelete(List)} and the validator.
 */
class TypesenseVectorStoreDeleteIdValidationTests {

	// ---------------------------------------------------------------------------
	// Characters that must be rejected (outside the allowlist)
	// ---------------------------------------------------------------------------

	@ParameterizedTest
	@ValueSource(chars = { ',', ']', '[', '|', '&', '`', '!', '=', '<', '>', '+', ' ', '\t', '\n' })
	void disallowedCharacterIsRejected(char disallowed) {
		String id = "prefix" + disallowed + "suffix";
		assertThatThrownBy(() -> TypesenseVectorStore.validateIdForFilter(id))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not allowed");
	}

	@Test
	void idConsistingOnlyOfDisallowedCharacterIsRejected() {
		assertThatThrownBy(() -> TypesenseVectorStore.validateIdForFilter(","))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void disallowedCharacterAtStartIsRejected() {
		assertThatThrownBy(() -> TypesenseVectorStore.validateIdForFilter("]trailing"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void disallowedCharacterAtEndIsRejected() {
		assertThatThrownBy(() -> TypesenseVectorStore.validateIdForFilter("leading|"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void openBracketIsRejected() {
		// [ is a list-open token in the filter_by grammar and is not in the allowlist.
		assertThatThrownBy(() -> TypesenseVectorStore.validateIdForFilter("id[0]"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not allowed");
	}

	@Test
	void leadingWhitespaceIsRejected() {
		// Leading whitespace is rejected rather than silently trimmed, because the
		// allowlist does not include whitespace characters.
		assertThatThrownBy(() -> TypesenseVectorStore.validateIdForFilter(" leading-space"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void trailingWhitespaceIsRejected() {
		assertThatThrownBy(() -> TypesenseVectorStore.validateIdForFilter("trailing-space "))
			.isInstanceOf(IllegalArgumentException.class);
	}

	// ---------------------------------------------------------------------------
	// Characters that must be accepted (inside the allowlist)
	// ---------------------------------------------------------------------------

	@Test
	void plainAlphanumericIdIsAccepted() {
		String id = "abc123";
		assertThat(TypesenseVectorStore.validateIdForFilter(id)).isEqualTo(id);
	}

	@Test
	void uuidIsAccepted() {
		String id = "550e8400-e29b-41d4-a716-446655440000";
		assertThat(TypesenseVectorStore.validateIdForFilter(id)).isEqualTo(id);
	}

	@Test
	void urnStyleIdWithColonIsAccepted() {
		// Colon is in the allowlist to support URN-style document IDs.
		String id = "urn:example:resource:42";
		assertThat(TypesenseVectorStore.validateIdForFilter(id)).isEqualTo(id);
	}

	@Test
	void idWithHyphenAndUnderscoreAndDotIsAccepted() {
		String id = "my-doc_id.v2";
		assertThat(TypesenseVectorStore.validateIdForFilter(id)).isEqualTo(id);
	}

	@Test
	void idWithSlashIsAccepted() {
		// Forward slash is in the allowlist to support path-like document IDs.
		String id = "path/to/resource";
		assertThat(TypesenseVectorStore.validateIdForFilter(id)).isEqualTo(id);
	}

	@Test
	void idWithAtSignIsAccepted() {
		// At-sign is in the allowlist to support email-style document IDs.
		String id = "user@domain";
		assertThat(TypesenseVectorStore.validateIdForFilter(id)).isEqualTo(id);
	}

	@Test
	void emptyIdIsAccepted() {
		// Validation does not enforce non-emptiness; that is the caller's concern.
		assertThat(TypesenseVectorStore.validateIdForFilter("")).isEqualTo("");
	}

	@Test
	void idIsReturnedUnchangedWhenValid() {
		String id = "valid-id_123";
		assertThat(TypesenseVectorStore.validateIdForFilter(id)).isSameAs(id);
	}

	// ---------------------------------------------------------------------------
	// Wiring tests: doDelete must call validateIdForFilter for every element
	// ---------------------------------------------------------------------------

	/**
	 * Builds a {@link TypesenseVectorStore} whose Typesense {@link Client} is a mock that
	 * stubs just enough to let {@code doDelete} reach its filter-building logic without
	 * throwing a NullPointerException. The {@link Documents#delete} stub returns a map
	 * with {@code num_deleted=1} so the warn-on-fewer-deletes branch is not triggered.
	 */
	private static TypesenseVectorStore storeWithMockedClient() throws Exception {
		Client client = mock(Client.class);
		Collection collection = mock(Collection.class);
		Documents documents = mock(Documents.class);

		when(client.collections(anyString())).thenReturn(collection);
		when(collection.documents()).thenReturn(documents);
		Map<String, Object> deleteResult = new HashMap<>();
		deleteResult.put("num_deleted", 1);
		when(documents.delete(any())).thenReturn(deleteResult);

		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(384);

		return TypesenseVectorStore.builder(client, embeddingModel)
			.collectionName("test_collection")
			.initializeSchema(false)
			.build();
	}

	@Test
	void doDeletePassesValidIdsToTheClient() throws Exception {
		TypesenseVectorStore store = storeWithMockedClient();
		// All IDs are valid; the mock client must be reached without an exception.
		store.doDelete(List.of("abc123", "urn:example:42", "path/to/doc"));
	}

	@Test
	void doDeleteRejectsInvalidIdAndDoesNotCallClient() throws Exception {
		Client client = mock(Client.class);
		Collection collection = mock(Collection.class);
		Documents documents = mock(Documents.class);
		when(client.collections(anyString())).thenReturn(collection);
		when(collection.documents()).thenReturn(documents);

		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(384);

		TypesenseVectorStore store = TypesenseVectorStore.builder(client, embeddingModel)
			.collectionName("test_collection")
			.initializeSchema(false)
			.build();

		// An ID with a comma must be rejected before the client is ever called.
		assertThatThrownBy(() -> store.doDelete(List.of("good-id", "bad,id")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not allowed");

		// The Typesense client must not have been called — no partial delete.
		verify(collection, never()).documents();
	}

	@Test
	void doDeleteRejectsListWithOpenBracket() throws Exception {
		TypesenseVectorStore store = storeWithMockedClient();
		assertThatThrownBy(() -> store.doDelete(List.of("id[0]"))).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not allowed");
	}

	@Test
	void doDeleteRejectsLeadingWhitespaceInsteadOfTrimming() throws Exception {
		// IDs with leading or trailing whitespace are rejected rather than silently
		// trimmed, because the allowlist does not include whitespace characters.
		TypesenseVectorStore store = storeWithMockedClient();
		assertThatThrownBy(() -> store.doDelete(List.of(" leading-space")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void doDeleteWithMixedValidAndInvalidIdRejectsAll() throws Exception {
		// The whole call fails if any one ID is invalid, to avoid a partial delete.
		TypesenseVectorStore store = storeWithMockedClient();
		assertThatThrownBy(() -> store.doDelete(List.of("valid-id", "bad id with space", "another-valid")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void doDeleteWithNullElementThrows() throws Exception {
		TypesenseVectorStore store = storeWithMockedClient();
		List<String> ids = new ArrayList<>();
		ids.add("valid-id");
		ids.add(null);
		assertThatThrownBy(() -> store.doDelete(ids)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void doDeleteWithNullListThrows() throws Exception {
		TypesenseVectorStore store = storeWithMockedClient();
		assertThatThrownBy(() -> store.doDelete((List<String>) null)).isInstanceOf(IllegalArgumentException.class);
	}

}

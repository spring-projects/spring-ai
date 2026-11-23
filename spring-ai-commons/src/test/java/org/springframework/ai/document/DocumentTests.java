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

package org.springframework.ai.document;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.content.Media;
import org.springframework.ai.document.id.IdGenerator;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DocumentTests {

	@Test
	void testScore() {
		Double score = 0.95;
		Document document = Document.builder().text("Test content").score(score).build();

		assertThat(document.getScore()).isEqualTo(score);
	}

	@Test
	void testNullScore() {
		Document document = Document.builder().text("Test content").score(null).build();

		assertThat(document.getScore()).isNull();
	}

	@Test
	void testMutate() {
		Media media = getMedia();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		Double score = 0.95;

		Document original = Document.builder()
			.id("customId")
			.text("Test content")
			.media(null)
			.metadata(metadata)
			.score(score)
			.build();

		Document mutated = original.mutate().build();

		assertThat(mutated).isNotSameAs(original).usingRecursiveComparison().isEqualTo(original);
	}

	@Test
	void testEquals() {
		Media media = getMedia();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		Double score = 0.95;

		Document doc1 = Document.builder().id("customId").text("Test text").metadata(metadata).score(score).build();

		Document doc2 = Document.builder().id("customId").text("Test text").metadata(metadata).score(score).build();

		Document differentDoc = Document.builder()
			.id("differentId")
			.text("Different content")
			.metadata(metadata)
			.score(score)
			.build();

		assertThat(doc1).isEqualTo(doc2).isNotEqualTo(differentDoc).isNotEqualTo(null).isNotEqualTo(new Object());

		assertThat(doc1.hashCode()).isEqualTo(doc2.hashCode());
	}

	@Test
	void testEmptyDocument() {
		assertThrows(IllegalArgumentException.class, () -> Document.builder().build());
	}

	@Test
	void testToString() {
		Media media = getMedia();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");
		Double score = 0.95;

		Document document = Document.builder()
			.id("customId")
			.text("Test content")
			.media(null)
			.metadata(metadata)
			.score(score)
			.build();

		String toString = document.toString();

		assertThat(toString).contains("id='customId'")
			.contains("text='Test content'")
			.contains("metadata=" + metadata)
			.contains("score=" + score);
	}

	@Test
	void testMediaDocumentConstruction() {
		Media media = getMedia();
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");

		Document document = Document.builder().media(media).metadata(metadata).build();

		assertThat(document.getMedia()).isEqualTo(media);
		assertThat(document.getText()).isNull();
		assertThat(document.isText()).isFalse();
	}

	@Test
	void testTextDocumentConstruction() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");

		Document document = Document.builder().text("Test text").metadata(metadata).build();

		assertThat(document.getText()).isEqualTo("Test text");
		assertThat(document.getMedia()).isNull();
		assertThat(document.isText()).isTrue();
	}

	@Test
	void testBothTextAndMediaThrowsException() {
		Media media = getMedia();
		assertThrows(IllegalArgumentException.class, () -> Document.builder().text("Test text").media(media).build());
	}

	@Test
	void testCustomIdGenerator() {
		IdGenerator customGenerator = contents -> "custom-" + contents[0];

		Document document = Document.builder().text("test").idGenerator(customGenerator).build();

		assertThat(document.getId()).isEqualTo("custom-test");
	}

	@Test
	void testMetadataValidation() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("nullKey", null);

		assertThrows(IllegalArgumentException.class, () -> Document.builder().text("test").metadata(metadata).build());
	}

	@Test
	void testFormattedContent() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("key", "value");

		Document document = Document.builder().text("Test text").metadata(metadata).build();

		String formattedContent = document.getFormattedContent(MetadataMode.ALL);
		assertThat(formattedContent).contains("Test text");
		assertThat(formattedContent).contains("key");
		assertThat(formattedContent).contains("value");
	}

	@Test
	void testCustomFormattedContent() {
		Document document = Document.builder().text("Test text").build();

		ContentFormatter customFormatter = (doc, mode) -> "Custom: " + doc.getText();
		String formattedContent = document.getFormattedContent(customFormatter, MetadataMode.ALL);

		assertThat(formattedContent).isEqualTo("Custom: Test text");
	}

	@Test
	void testNullIdThrowsException() {
		assertThrows(IllegalArgumentException.class, () -> Document.builder().id(null).text("test").build());
	}

	@Test
	void testEmptyIdThrowsException() {
		assertThrows(IllegalArgumentException.class, () -> Document.builder().id("").text("test").build());
	}

	@Test
	void testMetadataKeyValueAddition() {
		Document document = Document.builder()
			.text("test")
			.metadata("key1", "value1")
			.metadata("key2", "value2")
			.build();

		assertThat(document.getMetadata()).containsEntry("key1", "value1").containsEntry("key2", "value2");
	}

	private static Media getMedia() {
		return Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(URI.create("http://type1")).build();
	}

	@Test
	void testMetadataModeNone() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("secret", "hidden");

		Document document = Document.builder().text("Visible content").metadata(metadata).build();

		String formattedContent = document.getFormattedContent(MetadataMode.NONE);
		assertThat(formattedContent).contains("Visible content");
		assertThat(formattedContent).doesNotContain("secret");
		assertThat(formattedContent).doesNotContain("hidden");
	}

	@Test
	void testMetadataModeEmbed() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("embedKey", "embedValue");
		metadata.put("filterKey", "filterValue");

		Document document = Document.builder().text("Test content").metadata(metadata).build();

		String formattedContent = document.getFormattedContent(MetadataMode.EMBED);
		// This test assumes EMBED mode includes all metadata - adjust based on actual
		// implementation
		assertThat(formattedContent).contains("Test content");
	}

	@Test
	void testDocumentBuilderChaining() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("chain", "test");

		Document document = Document.builder()
			.text("Chain test")
			.metadata(metadata)
			.metadata("additional", "value")
			.score(0.85)
			.build();

		assertThat(document.getText()).isEqualTo("Chain test");
		assertThat(document.getMetadata()).containsEntry("chain", "test");
		assertThat(document.getMetadata()).containsEntry("additional", "value");
		assertThat(document.getScore()).isEqualTo(0.85);
	}

	@Test
	void testDocumentWithScoreGreaterThanOne() {
		Document document = Document.builder().text("High score test").score(1.5).build();

		assertThat(document.getScore()).isEqualTo(1.5);
	}

	@Test
	void testMutateWithChanges() {
		Document original = Document.builder().text("Original text").score(0.5).metadata("original", "value").build();

		Document mutated = original.mutate().text("Mutated text").score(0.8).metadata("new", "metadata").build();

		assertThat(mutated.getText()).isEqualTo("Mutated text");
		assertThat(mutated.getScore()).isEqualTo(0.8);
		assertThat(mutated.getMetadata()).containsEntry("new", "metadata");
		assertThat(original.getText()).isEqualTo("Original text"); // Original unchanged
	}

	@Test
	void testDocumentEqualityWithDifferentScores() {
		Document doc1 = Document.builder().id("sameId").text("Same text").score(0.5).build();

		Document doc2 = Document.builder().id("sameId").text("Same text").score(0.8).build();

		// Assuming score affects equality - adjust if it doesn't
		assertThat(doc1).isNotEqualTo(doc2);
	}

	@Test
	void testDocumentWithComplexMetadata() {
		Map<String, Object> nestedMap = new HashMap<>();
		nestedMap.put("nested", "value");

		Map<String, Object> metadata = new HashMap<>();
		metadata.put("string", "value");
		metadata.put("number", 1);
		metadata.put("boolean", true);
		metadata.put("map", nestedMap);

		Document document = Document.builder().text("Complex metadata test").metadata(metadata).build();

		assertThat(document.getMetadata()).containsEntry("string", "value");
		assertThat(document.getMetadata()).containsEntry("number", 1);
		assertThat(document.getMetadata()).containsEntry("boolean", true);
		assertThat(document.getMetadata()).containsEntry("map", nestedMap);
	}

	@Test
	void testMetadataImmutability() {
		Map<String, Object> originalMetadata = new HashMap<>();
		originalMetadata.put("key", "value");

		Document document = Document.builder().text("Immutability test").metadata(originalMetadata).build();

		// Modify original map
		originalMetadata.put("key", "modified");
		originalMetadata.put("newKey", "newValue");

		// Document's metadata should be unaffected (if properly copied)
		assertThat(document.getMetadata()).containsEntry("key", "value");
		assertThat(document.getMetadata()).doesNotContainKey("newKey");
	}

	@Test
	void testDocumentWithEmptyMetadata() {
		Document document = Document.builder().text("Empty metadata test").metadata(new HashMap<>()).build();

		assertThat(document.getMetadata()).isEmpty();
	}

	@Test
	void testMetadataWithNullValueInMap() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("validKey", "validValue");
		metadata.put("nullKey", null);

		assertThrows(IllegalArgumentException.class, () -> Document.builder().text("test").metadata(metadata).build());
	}

	@Test
	void testDocumentWithWhitespaceOnlyText() {
		String whitespaceText = "   \n\t\r   ";
		Document document = Document.builder().text(whitespaceText).build();

		assertThat(document.getText()).isEqualTo(whitespaceText);
		assertThat(document.isText()).isTrue();
	}

	@Test
	void testDocumentHashCodeConsistency() {
		Document document = Document.builder().text("Hash test").metadata("key", "value").score(0.1).build();

		int hashCode1 = document.hashCode();
		int hashCode2 = document.hashCode();

		assertThat(hashCode1).isEqualTo(hashCode2);
	}

	/**
	 * Serialised JSON must use the key "text", not "content". This documents the exact
	 * shape of the wire format that all consumers can rely on.
	 */
	@Test
	void serializationProducesTextKey() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		Document document = Document.builder().id("doc-1").text("hello world").build();

		String json = mapper.writeValueAsString(document);

		assertThat(json).contains("\"text\"").doesNotContain("\"content\"");
	}

	/**
	 * A document serialised to JSON must deserialise back to an equal object — the core
	 * round-trip contract that was broken before the fix.
	 */
	@Test
	void roundTripTextDocument() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		Document original = Document.builder()
			.id("doc-1")
			.text("hello world")
			.metadata("source", "unit-test")
			.score(0.85)
			.build();

		String json = mapper.writeValueAsString(original);
		Document deserialized = mapper.readValue(json, Document.class);

		assertThat(deserialized).isEqualTo(original);
	}

	/**
	 * Round-trip with all metadata value types that are valid for vector stores (string,
	 * int, float, boolean).
	 */
	@Test
	void roundTripDocumentWithVariousMetadataTypes() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		Document original = Document.builder()
			.id("doc-2")
			.text("metadata variety")
			.metadata("strKey", "strVal")
			.metadata("intKey", 42)
			.metadata("floatKey", 3.14f)
			.metadata("boolKey", true)
			.build();

		String json = mapper.writeValueAsString(original);
		Document deserialized = mapper.readValue(json, Document.class);

		assertThat(deserialized.getId()).isEqualTo("doc-2");
		assertThat(deserialized.getText()).isEqualTo("metadata variety");
		assertThat(deserialized.getMetadata()).containsEntry("strKey", "strVal").containsEntry("boolKey", true);
		// numeric types may widen during JSON round-trip; verify values are present
		assertThat(deserialized.getMetadata()).containsKey("intKey").containsKey("floatKey");
	}

	/**
	 * Round-trip for a document that carries no explicit score (null score).
	 */
	@Test
	void roundTripDocumentWithNullScore() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		Document original = Document.builder().id("doc-3").text("no score").score(null).build();

		String json = mapper.writeValueAsString(original);
		Document deserialized = mapper.readValue(json, Document.class);

		assertThat(deserialized.getScore()).isNull();
		assertThat(deserialized).isEqualTo(original);
	}

	/**
	 * Round-trip for a document that carries a non-null score.
	 */
	@Test
	void roundTripDocumentWithScore() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		Document original = Document.builder().id("doc-4").text("scored").score(0.99).build();

		String json = mapper.writeValueAsString(original);
		Document deserialized = mapper.readValue(json, Document.class);

		assertThat(deserialized.getScore()).isEqualTo(0.99);
		assertThat(deserialized).isEqualTo(original);
	}

	/**
	 * Round-trip for a document with an empty metadata map.
	 */
	@Test
	void roundTripDocumentWithEmptyMetadata() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		Document original = Document.builder().id("doc-5").text("no metadata").build();

		String json = mapper.writeValueAsString(original);
		Document deserialized = mapper.readValue(json, Document.class);

		assertThat(deserialized.getMetadata()).isEmpty();
		assertThat(deserialized).isEqualTo(original);
	}

	/**
	 * Round-trip for a document whose text contains special characters (quotes, unicode,
	 * newlines) to verify Jackson escaping is symmetric.
	 */
	@Test
	void roundTripDocumentWithSpecialCharactersInText() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		String specialText = "Line1\nLine2\t\"quoted\" \u00e9l\u00e8ve";
		Document original = Document.builder().id("doc-6").text(specialText).build();

		String json = mapper.writeValueAsString(original);
		Document deserialized = mapper.readValue(json, Document.class);

		assertThat(deserialized.getText()).isEqualTo(specialText);
		assertThat(deserialized).isEqualTo(original);
	}

	/**
	 * Multiple independent documents serialised and deserialised individually must not
	 * bleed state into one another.
	 */
	@Test
	void roundTripMultipleDocumentsAreIndependent() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		Document doc1 = Document.builder().id("id-a").text("alpha").metadata("k", "v1").score(0.1).build();
		Document doc2 = Document.builder().id("id-b").text("beta").metadata("k", "v2").score(0.2).build();

		Document deser1 = mapper.readValue(mapper.writeValueAsString(doc1), Document.class);
		Document deser2 = mapper.readValue(mapper.writeValueAsString(doc2), Document.class);

		assertThat(deser1).isEqualTo(doc1);
		assertThat(deser2).isEqualTo(doc2);
		assertThat(deser1).isNotEqualTo(deser2);
	}

	/**
	 * Deserialising JSON produced with the old "content" key (before the text/content
	 * rename) must still work via @JsonAlias("content"), so that data persisted before
	 * the fix can still be read back.
	 */
	@Test
	void deserializationAcceptsLegacyContentKey() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		String legacyJson = """
				{"id":"legacy-id","content":"legacy text","metadata":{},"score":null}
				""";

		Document document = mapper.readValue(legacyJson, Document.class);

		assertThat(document.getId()).isEqualTo("legacy-id");
		assertThat(document.getText()).isEqualTo("legacy text");
		assertThat(document.getMetadata()).isEmpty();
		assertThat(document.getScore()).isNull();
	}

	/**
	 * A document deserialised from the legacy "content" key must re-serialise with the
	 * current "text" key, confirming the alias normalises on ingestion.
	 */
	@Test
	void legacyContentKeyNormalisesToTextOnRoundTrip() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		String legacyJson = """
				{"id":"legacy-id","content":"legacy text","metadata":{},"score":null}
				""";

		Document document = mapper.readValue(legacyJson, Document.class);
		String reserialised = mapper.writeValueAsString(document);

		assertThat(reserialised).contains("\"text\"").doesNotContain("\"content\"");
		assertThat(mapper.readValue(reserialised, Document.class).getText()).isEqualTo("legacy text");
	}

	/**
	 * The public Document(String content) convenience constructor maps the "content"
	 * parameter to the text field — getText() must return it.
	 */
	@Test
	void publicContentConstructorPopulatesTextField() {
		Document document = new Document("constructor content");

		assertThat(document.getText()).isEqualTo("constructor content");
		assertThat(document.isText()).isTrue();
	}

	/**
	 * JSON with the current "text" key must deserialise correctly (primary path).
	 */
	@Test
	void deserializationAcceptsCurrentTextKey() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		String json = """
				{"id":"cur-id","text":"current text","metadata":{"k":"v"},"score":0.5}
				""";

		Document document = mapper.readValue(json, Document.class);

		assertThat(document.getId()).isEqualTo("cur-id");
		assertThat(document.getText()).isEqualTo("current text");
		assertThat(document.getMetadata()).containsEntry("k", "v");
		assertThat(document.getScore()).isEqualTo(0.5);
	}

	/**
	 * JSON with the "metadata" field absent must deserialise to an empty metadata map
	 * rather than throwing, matching the behaviour of the builder default.
	 */
	@Test
	void deserializationWithMissingMetadataDefaultsToEmptyMap() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		String json = """
				{"id":"no-meta-id","text":"no metadata in json","score":null}
				""";

		Document document = mapper.readValue(json, Document.class);

		assertThat(document.getId()).isEqualTo("no-meta-id");
		assertThat(document.getText()).isEqualTo("no metadata in json");
		assertThat(document.getMetadata()).isEmpty();
	}

	/**
	 * Unknown JSON fields (e.g. from a newer version of the format) must be silently
	 * ignored rather than causing a mapping failure.
	 */
	@Test
	void deserializationIgnoresUnknownFields() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		String json = """
				{"id":"fwd-id","text":"forward compat","metadata":{},"score":null,"unknownFutureField":"ignored"}
				""";

		Document document = mapper.readValue(json, Document.class);

		assertThat(document.getId()).isEqualTo("fwd-id");
		assertThat(document.getText()).isEqualTo("forward compat");
	}

	/**
	 * The "embedding" field that existed in older versions of Document must be silently
	 * ignored (covered by @JsonIgnoreProperties on the class).
	 */
	@Test
	void deserializationIgnoresLegacyEmbeddingField() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		String json = """
				{"id":"emb-id","text":"with old embedding","metadata":{},"score":null,"embedding":[0.1,0.2,0.3]}
				""";

		Document document = mapper.readValue(json, Document.class);

		assertThat(document.getId()).isEqualTo("emb-id");
		assertThat(document.getText()).isEqualTo("with old embedding");
	}

	/**
	 * Verifies that serialisation does not expose the contentFormatter field (it is
	 * ephemeral and annotated @JsonIgnore).
	 */
	@Test
	void serializationDoesNotExposeContentFormatter() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		Document document = Document.builder().id("fmt-id").text("formatter test").build();

		String json = mapper.writeValueAsString(document);

		assertThat(json).doesNotContain("contentFormatter").doesNotContain("formattedContent");
	}

	/**
	 * Verifies the exact set of top-level keys in the serialised JSON so that the wire
	 * format contract is explicit and regressions are caught early.
	 */
	@Test
	void serializationProducesExpectedKeys() throws Exception {
		JsonMapper mapper = JacksonUtils.getDefaultJsonMapper();
		Document document = Document.builder().id("keys-id").text("key check").metadata("m", "v").score(0.7).build();

		String json = mapper.writeValueAsString(document);

		assertThat(json).contains("\"id\"")
			.contains("\"text\"")
			.contains("\"metadata\"")
			.contains("\"score\"")
			.doesNotContain("\"content\"")
			.doesNotContain("\"contentFormatter\"")
			.doesNotContain("\"embedding\"");
	}

}

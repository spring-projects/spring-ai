package org.springframework.ai.vectorstore;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.util.Set;

/**
 * Registration of AOT hints for Pinecone's vector store.
 *
 * @author Josh Long
 *
 */
class PineconeVectorStoreHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		for (var t : Set.of(com.google.protobuf.Value.class, com.google.protobuf.Value.Builder.class,
				com.google.protobuf.Struct.class)) {
			hints.reflection().registerType(t, MemberCategory.values());
		}

	}

}

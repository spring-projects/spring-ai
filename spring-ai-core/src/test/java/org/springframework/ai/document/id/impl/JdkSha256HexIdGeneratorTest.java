package org.springframework.ai.document.id.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class JdkSha256HexIdGeneratorTest {

	private final JdkSha256HexIdGenerator testee = new JdkSha256HexIdGenerator();

	@Test
	void get_message_digest_returns_distinct_instances() {
		// Initialize

		// Run
		final MessageDigest md1 = testee.getMessageDigest();
		final MessageDigest md2 = testee.getMessageDigest();

		// Assert (main relations to be tested)
		Assertions.assertThat(md1 != md2).isTrue();

		// Assert (other expected relations)
		Assertions.assertThat(md1.getAlgorithm()).isEqualTo(md2.getAlgorithm());
		Assertions.assertThat(md1.getDigestLength()).isEqualTo(md2.getDigestLength());
		Assertions.assertThat(md1.getProvider()).isEqualTo(md2.getProvider());
		Assertions.assertThat(md1.toString()).isEqualTo(md2.toString());
	}

	@Test
	void get_message_digest_returns_instances_with_independent_and_reproducible_digests() {
		// Initialize
		final String updateString1 = "md1_update";
		final String updateString2 = "md2_update";
		final Charset charset = StandardCharsets.UTF_8;

		// Run
		final byte[] md1BytesFirstTry = testee.getMessageDigest().digest(updateString1.getBytes(charset));
		final byte[] md2BytesFirstTry = testee.getMessageDigest().digest(updateString2.getBytes(charset));
		final byte[] md1BytesSecondTry = testee.getMessageDigest().digest(updateString1.getBytes(charset));
		final byte[] md2BytesSecondTry = testee.getMessageDigest().digest(updateString2.getBytes(charset));

		// Assert (distinction)
		Assertions.assertThat(md1BytesFirstTry).isNotEqualTo(md2BytesFirstTry);

		// Assert (reproducibility)
		Assertions.assertThat(md1BytesFirstTry).isEqualTo(md1BytesSecondTry);
		Assertions.assertThat(md2BytesFirstTry).isEqualTo(md2BytesSecondTry);
	}

}
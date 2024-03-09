/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.document.id;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class JdkSha256HexIdGeneratorTest {

	private final JdkSha256HexIdGenerator testee = new JdkSha256HexIdGenerator();

	@Test
	void messageDigestReturnsDistinctInstances() {
		final MessageDigest md1 = testee.getMessageDigest();
		final MessageDigest md2 = testee.getMessageDigest();

		Assertions.assertThat(md1 != md2).isTrue();

		Assertions.assertThat(md1.getAlgorithm()).isEqualTo(md2.getAlgorithm());
		Assertions.assertThat(md1.getDigestLength()).isEqualTo(md2.getDigestLength());
		Assertions.assertThat(md1.getProvider()).isEqualTo(md2.getProvider());
		Assertions.assertThat(md1.toString()).isEqualTo(md2.toString());
	}

	@Test
	void messageDigestReturnsInstancesWithIndependentAndReproducibleDigests() {
		final String updateString1 = "md1_update";
		final String updateString2 = "md2_update";
		final Charset charset = StandardCharsets.UTF_8;

		final byte[] md1BytesFirstTry = testee.getMessageDigest().digest(updateString1.getBytes(charset));
		final byte[] md2BytesFirstTry = testee.getMessageDigest().digest(updateString2.getBytes(charset));
		final byte[] md1BytesSecondTry = testee.getMessageDigest().digest(updateString1.getBytes(charset));
		final byte[] md2BytesSecondTry = testee.getMessageDigest().digest(updateString2.getBytes(charset));

		Assertions.assertThat(md1BytesFirstTry).isNotEqualTo(md2BytesFirstTry);

		Assertions.assertThat(md1BytesFirstTry).isEqualTo(md1BytesSecondTry);
		Assertions.assertThat(md2BytesFirstTry).isEqualTo(md2BytesSecondTry);
	}

}
package org.springframework.ai.document.id.impl;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class JdkSha256HexIdGenerator extends HashBaseIdGenerator {

	private final String byteHexFormat = "%02x";

	private final String algorithm;

	private final Charset charset;

	public JdkSha256HexIdGenerator(final String algorithm, final Charset charset) {
		this.algorithm = algorithm;
		this.charset = charset;
	}

	public JdkSha256HexIdGenerator() {
		this("SHA-256", StandardCharsets.UTF_8);
	}

	// https://github.com/spring-projects/spring-ai/issues/113#issue-2000373318
	@Override
	String hash(String contentWithMetadata) {
		try {
			byte[] hashBytes = MessageDigest.getInstance(algorithm).digest(contentWithMetadata.getBytes(charset));
			StringBuilder sb = new StringBuilder();
			for (byte b : hashBytes) {
				sb.append(String.format(byteHexFormat, b));
			}
			return UUID.nameUUIDFromBytes(sb.toString().getBytes(charset)).toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException(e);
		}
	}

}

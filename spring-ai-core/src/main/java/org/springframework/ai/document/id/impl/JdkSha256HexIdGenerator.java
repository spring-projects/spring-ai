package org.springframework.ai.document.id.impl;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class JdkSha256HexIdGenerator extends HashBaseIdGenerator {

	private final String byteHexFormat = "%02x";

	private final Charset charset;

	private final MessageDigest messageDigest;

	public JdkSha256HexIdGenerator(final String algorithm, final Charset charset) {
		this.charset = charset;
		try {
			this.messageDigest = MessageDigest.getInstance(algorithm);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException(e);
		}
	}

	public JdkSha256HexIdGenerator() {
		this("SHA-256", StandardCharsets.UTF_8);
	}

	public MessageDigest getMessageDigest() {
		try {
			return (MessageDigest) messageDigest.clone();
		}
		catch (CloneNotSupportedException e) {
			throw new RuntimeException("Unsupported clone for MessageDigest.", e);
		}
	}

	// https://github.com/spring-projects/spring-ai/issues/113#issue-2000373318
	@Override
	String hash(String contentWithMetadata) {
		byte[] hashBytes = getMessageDigest().digest(contentWithMetadata.getBytes(charset));
		StringBuilder sb = new StringBuilder();
		for (byte b : hashBytes) {
			sb.append(String.format(byteHexFormat, b));
		}
		return UUID.nameUUIDFromBytes(sb.toString().getBytes(charset)).toString();
	}

}

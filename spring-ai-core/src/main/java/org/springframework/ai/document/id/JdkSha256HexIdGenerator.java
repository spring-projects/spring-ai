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

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.springframework.util.Assert;

/**
 * A SHA-256 based ID generator that returns the hash as a UUID.
 *
 * @author Aliakbar Jafarpour
 * @author Christian Tzolov
 */
public class JdkSha256HexIdGenerator implements IdGenerator {

	private static final String SHA_256 = "SHA-256";

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
		this(SHA_256, StandardCharsets.UTF_8);
	}

	@Override
	public String generateId(Object... contents) {
		return this.hash(this.serializeToBytes(contents));
	}

	// https://github.com/spring-projects/spring-ai/issues/113#issue-2000373318
	private String hash(byte[] contentWithMetadata) {
		byte[] hashBytes = getMessageDigest().digest(contentWithMetadata);
		StringBuilder sb = new StringBuilder();
		for (byte b : hashBytes) {
			sb.append(String.format(this.byteHexFormat, b));
		}
		return UUID.nameUUIDFromBytes(sb.toString().getBytes(this.charset)).toString();
	}

	private byte[] serializeToBytes(Object... contents) {
		Assert.notNull(contents, "Contents must not be null");
		ByteArrayOutputStream byteOut = null;
		try {
			byteOut = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(byteOut);
			for (Object content : contents) {
				out.writeObject(content);
			}
			return byteOut.toByteArray();
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to serialize", e);
		}
		finally {
			if (byteOut != null) {
				try {
					byteOut.close();
				}
				catch (Exception e) {
					// ignore
				}
			}
		}
	}

	MessageDigest getMessageDigest() {
		try {
			return (MessageDigest) messageDigest.clone();
		}
		catch (CloneNotSupportedException e) {
			throw new RuntimeException("Unsupported clone for MessageDigest.", e);
		}
	}

}

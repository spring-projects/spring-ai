package org.springframework.ai.document.id.impl;

import org.apache.commons.codec.digest.DigestUtils;

public class ApacheSha256HexIdGenerator extends HashBaseIdGenerator {

	@Override
	public String hash(String contentWithMetadata) {
		return DigestUtils.sha256Hex(contentWithMetadata);
	}

}

package org.springframework.ai.document.id.impl;

import org.apache.commons.codec.digest.DigestUtils;

public class ApacheShaThree256HexIdGenerator extends HashBaseIdGenerator {

	@Override
	public String hash(String contentWithMetadata) {
		return DigestUtils.sha3_256Hex(contentWithMetadata);
	}

}

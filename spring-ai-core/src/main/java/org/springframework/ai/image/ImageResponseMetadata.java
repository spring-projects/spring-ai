package org.springframework.ai.image;

import org.springframework.ai.model.ResponseMetadata;

public interface ImageResponseMetadata extends ResponseMetadata {

	ImageResponseMetadata NULL = new ImageResponseMetadata() {
	};

	default Long created() {
		return System.currentTimeMillis();
	}

}

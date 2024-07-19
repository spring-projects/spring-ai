package org.springframework.ai.model;

import java.util.Collection;

public interface MediaContent extends Content {

	/**
	 * Get the media associated with the content.
	 */
	Collection<Media> getMedia();

}

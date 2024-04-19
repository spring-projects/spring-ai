package org.springframework.ai.model;

import org.springframework.ai.chat.messages.Media;

import java.util.List;
import java.util.Map;

/**
 * A simple data structure that contains content and metadata.
 *
 * @param <T> the type of content in the node
 * @author Mark Pollack
 * @since 1.0 M1
 */
public interface Content {

	String getContent();

	List<Media> getMedia();

	Map<String, Object> getMetadata();

}

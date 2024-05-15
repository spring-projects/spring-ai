package org.springframework.ai.model;

import org.springframework.ai.chat.messages.Media;

import java.util.List;
import java.util.Map;

/**
 * Data structure that contains content and metadata. Common parent for the
 * {@link org.springframework.ai.document.Document} and the
 * {@link org.springframework.ai.chat.messages.Message} classes.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0.0
 */
public interface Content {

	/**
	 * Get the content of the message.
	 */
	String getContent(); // TODO consider getText

	/**
	 * Get the media associated with the content.
	 */
	List<Media> getMedia();

	/**
	 * return Get the metadata associated with the content.
	 */
	Map<String, Object> getMetadata();

}

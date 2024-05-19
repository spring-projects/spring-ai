package org.springframework.ai.model;

import org.springframework.ai.chat.messages.Media;

import java.util.Collection;
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
	default Collection<Media> getMedia() {
		return getMedia("");
	}

	/**
	 * Retrieves the collection of media attachments associated with the content.
	 * @param dummy a dummy parameter to ensure method signature uniqueness
	 * @return a list of Media objects representing the media attachments
	 * @deprecated This method is deprecated since version 1.0.0 M1 and will be removed in
	 * a future release
	 */
	@Deprecated(since = "1.0.0 M1", forRemoval = true)
	List<Media> getMedia(String... dummy);

	/**
	 * return Get the metadata associated with the content.
	 */
	Map<String, Object> getMetadata();

}

package org.springframework.ai.chat.client.advisor;

import java.util.Map;

/**
 * A {@link UserTextProcessor} that returns the user text as is.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class NoOpUserTextProcessor implements UserTextProcessor {

	@Override
	public String process(String userText, Map<String, Object> userParams) {
		return userText;
	}

}

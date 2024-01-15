package org.springframework.ai.generative;

import java.util.List;

/**
 * Represents the response from a generative model process. In the context of DDD, this
 * interface can be seen as an entity within the core domain. It encapsulates the
 * generative output (or outputs) along with response metadata (value objects).
 *
 * @param <T> The data type of the generative output, integral to the ubiquitous language
 * of the generative AI models.
 * @author Mark Pollack
 * @since 0.8.0
 */
public interface GenerativeResponse<T> {

	T getGeneration();

	List<T> getGenerations();

	ResponseMetadata getMetadata();

}

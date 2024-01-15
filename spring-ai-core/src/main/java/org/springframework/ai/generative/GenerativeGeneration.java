package org.springframework.ai.generative;

/**
 * GenerativeGeneration interface encapsulates the output of a single generative
 * operation. In DDD terms, it acts as an entity within the core domain, capturing
 * specific generative outputs, and is associated with its metadata (a value object).
 *
 * @param <T> The data type of the generative output, aligning with the ubiquitous
 * language of the specific generative model.
 * @author Mark Pollack
 * @since 0.8.0
 */
public interface GenerativeGeneration<T> {

	T getOutput();

	GenerationMetadata getGenerationMetadata();

}

package org.springframework.ai.generative;

/**
 * GenerativePrompt defines the structure for inputs required by generative models. In
 * DDD, this interface is a crucial part of the core domain and serves as a value object
 * that encapsulates the instructions (entities) for the generative process.
 *
 * @param <T> The type of instruction data required by the generative model, which forms
 * part of the ubiquitous language in the specific generative context.
 * @author Mark Pollack
 * @since 0.8.0
 */
public interface GenerativePrompt<T> {

	T getInstructions(); // required input

	Options getOptions();

}
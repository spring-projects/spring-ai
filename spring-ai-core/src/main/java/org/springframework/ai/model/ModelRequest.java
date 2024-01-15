package org.springframework.ai.model;

/**
 * Interface representing a request to an AI model. This interface encapsulates the
 * necessary information required to interact with an AI model, including instructions or
 * inputs (of generic type T) and additional model options. It provides a standardized way
 * to send requests to AI models, ensuring that all necessary details are included and can
 * be easily managed.
 *
 * @param <T> the type of instructions or input required by the AI model
 * @author Mark Pollack
 * @since 0.8.0
 */
public interface ModelRequest<T> {

	/**
	 * Retrieves the instructions or input required by the AI model.
	 * @return the instructions or input required by the AI model
	 */
	T getInstructions(); // required input

	/**
	 * Retrieves the customizable options for AI model interactions.
	 * @return the customizable options for AI model interactions
	 */
	ModelOptions getOptions();

}
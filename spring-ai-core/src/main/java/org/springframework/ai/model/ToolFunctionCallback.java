package org.springframework.ai.model;

/**
 * Represents a model function call handler. Implementations are registered with the
 * Models and called on prompts that trigger the function call.
 *
 * @author Christian Tzolov
 */
public interface ToolFunctionCallback {

	/**
	 * @return Returns the Function name. Unique within the model.
	 */
	public String getName();

	/**
	 * @return Returns the function description. This description is used by the model do
	 * decide if the function should be called or not.
	 */
	public String getDescription();

	/**
	 * @return Returns the JSON schema of the function input type.
	 */
	public String getInputTypeSchema();

	/**
	 * Called when a model detects and triggers a function call. The model is responsible
	 * to pass the function arguments in the pre-configured JSON schema format.
	 * @param functionArguments JSON string with the function arguments to be passed to
	 * the function. The arguments are defined as JSON schema usually registered with the
	 * the model.
	 * @return String containing the function call response.
	 */
	public String call(String functionArguments);

}
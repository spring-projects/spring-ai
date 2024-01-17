package org.springframework.ai.model;

/**
 * The ModelClient interface provides a generic API for invoking AI models. It is designed
 * to handle the interaction with various types of AI models by abstracting the process of
 * sending requests and receiving responses. The interface uses Java generics to
 * accommodate different types of requests and responses, enhancing flexibility and
 * adaptability across different AI model implementations.
 *
 * @param <TReq> the generic type of the request to the AI model
 * @param <TRes> the generic type of the response from the AI model
 * @author Mark Pollack
 * @since 0.8.0
 */
public interface ModelClient<TReq extends ModelRequest<?>, TRes extends ModelResponse<?>> {

	/**
	 * Executes a method call to the AI model.
	 * @param request the request object to be sent to the AI model
	 * @param <TReq> the generic type of the request object
	 * @param <TRes> the generic type of the response from the AI model
	 * @return the response from the AI model
	 */
	TRes call(TReq request);

}

package org.springframework.ai.model;

/**
 * The ModelCall interface provides a generic API for invoking AI models. It is designed
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
public interface ModelCall<TReq extends ModelRequest<?>, TRes extends ModelResponse<?>> {

	TRes call(TReq request);

}

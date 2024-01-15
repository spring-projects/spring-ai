package org.springframework.ai.generative;

/**
 * The GenerativeClient interface represents the aggregate root in the core domain of
 * generative AI systems. It's responsible for orchestrating the generative process,
 * adhering to the ubiquitous language of AI generation.
 *
 * @param <TReq> The type of GenerativePrompt, adhering to the specific requirements of
 * generative AI models.
 * @param <TRes> The type of GenerativeResponse, representing the output from the
 * generative process.
 * @author Mark Pollack
 * @since 0.8.0
 */
public interface GenerativeClient<TReq extends GenerativePrompt<?>, TRes extends GenerativeResponse<?>> {

	TRes generate(TReq prompt);

}

package org.springframework.ai.generative;

public interface GenerativeClient<TReq extends GenerativePrompt<?>, TRes extends GenerativeResponse<?>> {

	TRes generate(TReq prompt);

}

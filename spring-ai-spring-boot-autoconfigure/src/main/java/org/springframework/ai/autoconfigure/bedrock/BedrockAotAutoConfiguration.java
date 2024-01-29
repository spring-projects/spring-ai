package org.springframework.ai.autoconfigure.bedrock;

import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi;
import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@ConditionalOnClass({ Ai21Jurassic2ChatBedrockApi.class, CohereChatBedrockApi.class, CohereEmbeddingBedrockApi.class,
		Llama2ChatBedrockApi.class, TitanChatBedrockApi.class, TitanEmbeddingBedrockApi.class,
		AnthropicChatBedrockApi.class })
class BedrockAotAutoConfiguration {

	@Bean
	static BedrockAiHints bedrockAiHints() {
		return new BedrockAiHints();
	}

	static class BedrockAiHints implements BeanRegistrationAotProcessor {

		@Override
		public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
			return (generationContext, beanRegistrationCode) -> {
				var hints = generationContext.getRuntimeHints();
				var mcs = MemberCategory.values();
				for (var c : Set.of(Ai21Jurassic2ChatBedrockApi.class, CohereChatBedrockApi.class,
						CohereEmbeddingBedrockApi.class, Llama2ChatBedrockApi.class, TitanChatBedrockApi.class,
						TitanEmbeddingBedrockApi.class, AnthropicChatBedrockApi.class))
					hints.reflection().registerType(c, mcs);

			};
		}

	}

}

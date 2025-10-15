package org.springframework.ai.model.azure.openai.autoconfigure;

import com.azure.ai.openai.OpenAIClientBuilder;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * {@link AutoConfiguration Auto-configuration} for Azure OpenAI.
 *
 * @author Piotr Olaszewski
 * @author Soby Chacko
 * @author Manuel Andreo Garcia
 * @author Ilayaperumal Gopinathan
 */
@AutoConfiguration(after = { ToolCallingAutoConfiguration.class })
@ConditionalOnClass({ AzureOpenAiChatModel.class })
@EnableConfigurationProperties({ AzureOpenAiChatProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.CHAT_MODEL, havingValue = SpringAIModels.AZURE_OPENAI,
		matchIfMissing = true)
@Import(AzureOpenAiClientBuilderConfiguration.class)
public class AzureOpenAiChatAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public AzureOpenAiChatModel azureOpenAiChatModel(OpenAIClientBuilder openAIClientBuilder,
			AzureOpenAiChatProperties chatProperties, ToolCallingManager toolCallingManager,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<ChatModelObservationConvention> observationConvention,
			ObjectProvider<ToolExecutionEligibilityPredicate> azureOpenAiToolExecutionEligibilityPredicate) {

		var chatModel = AzureOpenAiChatModel.builder()
			.openAIClientBuilder(openAIClientBuilder)
			.defaultOptions(chatProperties.getOptions())
			.toolCallingManager(toolCallingManager)
			.toolExecutionEligibilityPredicate(azureOpenAiToolExecutionEligibilityPredicate
				.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.build();
		observationConvention.ifAvailable(chatModel::setObservationConvention);

		return chatModel;
	}

}

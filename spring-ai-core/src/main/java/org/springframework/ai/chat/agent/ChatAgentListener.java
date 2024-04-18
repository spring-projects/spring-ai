package org.springframework.ai.chat.agent;

public interface ChatAgentListener {

	void onComplete(AgentResponse agentResponse);

}

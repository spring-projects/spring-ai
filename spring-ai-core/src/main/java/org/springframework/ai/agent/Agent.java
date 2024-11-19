package org.springframework.ai.agent;

import org.springframework.ai.chat.messages.InstructionMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * The Agent class references the openAI swarm open source framework, which can call tool
 * functions and transfer tasks to other AI agents.
 * <p>
 * for example : transfer singing task to agentB
 * <p>
 * <code>
 * 		   Agent agentA = new Agent("agentA", new InstructionMessage("You are an intelligent butler, skilled at getting professionals to do professional things. by the way agentB skilled in singing"),
 *                 ollamaChatModel);

 *         Agent agentB = new Agent("agentB", new InstructionMessage("You are a singer, skilled in singing"),
 *                 ollamaChatModel);

 *         ArrayList<FunctionCallback> functionCallbacks = new ArrayList<>();
 *         functionCallbacks.add(FunctionCallbackWrapper.builder(new Function<Request, Agent>() {
 *                     public Agent apply(Request request) {
 *                         return agentB;
 *                     }
 *                 }).withDescription("You can call this function to sing")
 *                 .withName("assign task to agentB")
 *                 .build());
 *         agentA.setFunctionCallbacks(functionCallbacks);

 *         String response = agentA.call("Hello, sing me a song");
 * </code>
 *
 * @author wanglei
 * @since 1.0.0 M1
 */
public class Agent implements ChatModel {

	/**
	 * agent name
	 */
	private String agentName;

	/**
	 * every agent can have a first instruction to describe what can it do
	 */
	private InstructionMessage instructionMessage;

	/**
	 * chatModel
	 */
	private ChatModel chatModel;

	/**
	 * functionCallback
	 */
	private List<FunctionCallback> functionCallbacks;

	/**
	 * agent call for chatResponse
	 * @param prompt the request object to be sent to the AI model
	 * @return
	 */
	@Override
	public ChatResponse call(Prompt prompt) {

		Prompt copyPrompt = prompt.copy();
		if (instructionMessage != null) {
			List<Message> instructions = copyPrompt.getInstructions();
			// remove other agent instruction
			instructions.removeIf(InstructionMessage.class::isInstance);
			instructions.add(0, instructionMessage);
		}

		ChatOptions chatModelOptions = chatModel.getDefaultOptions();
		if (chatModelOptions instanceof FunctionCallingOptions) {
			// support functionCalling
			// add functionCallBacks to conversion
			ChatOptions copyChatOptions = copyPrompt.getOptions();
			if (null == copyChatOptions) {
				ChatOptions copyChatModelOption = chatModelOptions.copy();
				FunctionCallingOptions copyChatModelFunctionOptions = (FunctionCallingOptions) copyChatModelOption
					.copy();
				copyChatModelFunctionOptions.setFunctionCallbacks(functionCallbacks);
				copyPrompt = new Prompt(copyPrompt.getInstructions(), copyChatModelFunctionOptions);

			}
			else if (copyChatOptions instanceof FunctionCallingOptions copyChatFunctionCallingOptions) {
				copyChatFunctionCallingOptions.setFunctionCallbacks(functionCallbacks);
			}
		}

		return chatModel.call(copyPrompt);
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return chatModel.getDefaultOptions();
	}

	/**
	 * agent stream method
	 * @param prompt the request object to be sent to the AI model
	 * @return
	 */
	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		Prompt copyPrompt = prompt.copy();
		if (instructionMessage != null) {
			List<Message> instructions = copyPrompt.getInstructions();
			// remove other agent instruction
			instructions.removeIf(InstructionMessage.class::isInstance);
			instructions.add(0, instructionMessage);
		}

		ChatOptions chatModelOptions = chatModel.getDefaultOptions();
		if (chatModelOptions instanceof FunctionCallingOptions) {
			// support functionCalling
			// add functionCallBacks to conversion
			ChatOptions copyChatOptions = copyPrompt.getOptions();
			if (null == copyChatOptions) {
				ChatOptions copyChatModelOption = chatModelOptions.copy();
				FunctionCallingOptions copyChatModelFunctionOptions = (FunctionCallingOptions) copyChatModelOption
					.copy();
				copyChatModelFunctionOptions.setFunctionCallbacks(functionCallbacks);
				copyPrompt = new Prompt(copyPrompt.getInstructions(), copyChatModelFunctionOptions);

			}
			else if (copyChatOptions instanceof FunctionCallingOptions copyChatFunctionCallingOptions) {
				copyChatFunctionCallingOptions.setFunctionCallbacks(functionCallbacks);
			}
		}

		return chatModel.stream(copyPrompt);
	}

	public Agent() {
	}

	public Agent(String agentName, InstructionMessage instructionMessage, ChatModel chatModel,
			List<FunctionCallback> functionCallbacks) {
		this.agentName = agentName;
		this.instructionMessage = instructionMessage;
		this.chatModel = chatModel;
		this.functionCallbacks = functionCallbacks;
	}

	public Agent(String agentName, InstructionMessage instructionMessage, ChatModel chatModel) {
		this(agentName, instructionMessage, chatModel, null);
	}

	public Agent(String agentName, ChatModel chatModel) {
		this(agentName, null, chatModel);
	}

	public String getAgentName() {
		return agentName;
	}

	public void setAgentName(String agentName) {
		this.agentName = agentName;
	}

	public ChatModel getChatModel() {
		return chatModel;
	}

	public void setChatModel(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	public List<FunctionCallback> getFunctionCallbacks() {
		return functionCallbacks;
	}

	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.functionCallbacks = functionCallbacks;
	}

}

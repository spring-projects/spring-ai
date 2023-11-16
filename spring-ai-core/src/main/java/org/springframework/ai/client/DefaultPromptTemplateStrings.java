package org.springframework.ai.client;

public interface DefaultPromptTemplateStrings {

    public static final String RAG_PROMPT = """
            You are a helpful assistant, conversing with a user about the subjects contained in a set of documents.
            Use the information from the DOCUMENTS section to provide accurate answers. If unsure, simply state
            that you don't know the answer.
            
            QUESTION:
            {input}
            
            DOCUMENTS:
            {documents}""";

    public static final String CHAT_PROMPT = """
        The following is a friendly conversation between a human and an AI. The AI is talkative and provides lots of 
        specific details from its context. If the AI does not know the answer to a question, it truthfully says it does 
        not know.

    	Current conversation:
	    {history}
	    Human: {input}
	    AI:""";

}

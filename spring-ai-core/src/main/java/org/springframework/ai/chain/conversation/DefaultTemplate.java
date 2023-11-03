package org.springframework.ai.chain.conversation;

import org.springframework.ai.prompt.PromptTemplate;

interface DefaultTemplate {

    static final String DEFAULT_TEMPLATE_STRING = """
    The following is a friendly conversation between a human and an AI. The AI is talkative and provides lots of specific details from its context. If the AI does not know the answer to a question, it truthfully says it does not know.

    Current conversation:
    {history}
    Human: {input}
    AI:""";

    static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate(DEFAULT_TEMPLATE_STRING);

}

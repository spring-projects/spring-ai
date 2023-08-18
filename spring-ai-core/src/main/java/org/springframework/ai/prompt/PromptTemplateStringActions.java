package org.springframework.ai.prompt;

import java.util.Map;

public interface PromptTemplateStringActions {

	String render();

	String render(Map<String, Object> model);

}

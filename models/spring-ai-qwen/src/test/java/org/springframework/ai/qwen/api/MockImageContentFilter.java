package org.springframework.ai.qwen.api;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.common.MultiModalMessage;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MockImageContentFilter {

	static void handle(MultiModalConversationParam.MultiModalConversationParamBuilder<?, ?> builder) {
		List<Map<String, Object>> filteredContents = new LinkedList<>();
		List<MultiModalMessage> filteredMessages = new LinkedList<>();
		boolean customized = false;

		for (Object message : builder.build().getMessages()) {
			MultiModalMessage multiModalMessage = (MultiModalMessage) message;
			for (Map<String, Object> content : multiModalMessage.getContent()) {
				Map<String, Object> filteredContent = CollectionUtils.newHashMap(1);
				for (String key : content.keySet()) {
					Object value = content.get(key);
					if ("image".equals(key)) {
						String imageUrl = (String) content.get("image");
						if (StringUtils.hasText(imageUrl)) {
							// Maybe an invalid image. Replace with a default one.
							value = "https://avatars.githubusercontent.com/u/317776";
							customized = true;
						}
					}
					filteredContent.put(key, value);
				}
				filteredContents.add(filteredContent);
			}
			// @formatter:off
            MultiModalMessage filteredMessage = MultiModalMessage.builder()
                    .role(multiModalMessage.getRole())
                    .content(filteredContents)
                    .build();
            // @formatter:on
			filteredMessages.add(filteredMessage);
		}

		if (customized) {
			builder.clearMessages();
			builder.messages(filteredMessages);
		}
	}

}

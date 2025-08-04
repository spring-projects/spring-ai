package org.springframework.ai.qwen.api;

import org.springframework.ai.model.ChatModelDescription;

public enum QwenModel implements ChatModelDescription {

	QWEN_TURBO("qwen-turbo", "Qwen base model, stable version."),
	QWEN_TURBO_LATEST("qwen-turbo-latest", "Qwen base model, latest version."),
	QWEN_PLUS("qwen-plus", "Qwen plus model, stable version."),
	QWEN_PLUS_LATEST("qwen-plus-latest", "Qwen plus model, latest version."),
	QWEN_MAX("qwen-max", "Qwen max model, stable version."),
	QWEN_MAX_LATEST("qwen-max-latest", "Qwen max model, latest version."),
	QWEN_LONG("qwen-long", "Qwen long model, 10m context."),
	QWEN_7B_CHAT("qwen-7b-chat", "Qwen open sourced 7-billion-parameters model."),
	QWEN_14B_CHAT("qwen-14b-chat", "Qwen open sourced 14-billion-parameters model."),
	QWEN_72B_CHAT("qwen-72b-chat", "Qwen open sourced 72-billion-parameters model."),
	QWEN1_5_7B_CHAT("qwen1.5-7b-chat", "Qwen open sourced 7-billion-parameters model (v1.5)."),
	QWEN1_5_14B_CHAT("qwen1.5-14b-chat", "Qwen open sourced 14-billion-parameters model (v1.5)."),
	QWEN1_5_32B_CHAT("qwen1.5-32b-chat", "Qwen open sourced 32-billion-parameters model (v1.5)."),
	QWEN1_5_72B_CHAT("qwen1.5-72b-chat", "Qwen open sourced 72-billion-parameters model (v1.5)."),
	QWEN2_0_5B_INSTRUCT("qwen2-0.5b-instruct", "Qwen open sourced 0.5-billion-parameters model (v2)."),
	QWEN2_1_5B_INSTRUCT("qwen2-1.5b-instruct", "Qwen open sourced 1.5-billion-parameters model (v2)."),
	QWEN2_7B_INSTRUCT("qwen2-7b-instruct", "Qwen open sourced 7-billion-parameters model (v2)."),
	QWEN2_72B_INSTRUCT("qwen2-72b-instruct", "Qwen open sourced 72-billion-parameters model (v2)."),
	QWEN2_57B_A14B_INSTRUCT("qwen2-57b-a14b-instruct",
			"Qwen open sourced 57-billion-parameters and 14-billion-activation-parameters MOE model (v2)."),
	QWEN2_5_0_5B_INSTRUCT("qwen2.5-0.5b-instruct", "Qwen open sourced 0.5-billion-parameters model (v2.5)."),
	QWEN2_5_1_5B_INSTRUCT("qwen2.5-1.5b-instruct", "Qwen open sourced 1.5-billion-parameters model (v2.5)."),
	QWEN2_5_3B_INSTRUCT("qwen2.5-3b-instruct", "Qwen open sourced 3-billion-parameters model (v2.5)."),
	QWEN2_5_7B_INSTRUCT("qwen2.5-7b-instruct", "Qwen open sourced 7-billion-parameters model (v2.5)."),
	QWEN2_5_14B_INSTRUCT("qwen2.5-14b-instruct", "Qwen open sourced 14-billion-parameters model (v2.5)."),
	QWEN2_5_32B_INSTRUCT("qwen2.5-32b-instruct", "Qwen open sourced 32-billion-parameters model (v2.5)."),
	QWEN2_5_72B_INSTRUCT("qwen2.5-72b-instruct", "Qwen open sourced 72-billion-parameters model (v2.5)."),
	QWEN_VL_PLUS("qwen-vl-plus", "Qwen multi-modal model, supports image and text information, stable version."),
	QWEN_VL_PLUS_LATEST("qwen-vl-plus-latest",
			"Qwen multi-modal model, supports image and text information, latest version."),
	QWEN_VL_MAX("qwen-vl-max",
			"Qwen multi-modal model, supports image and text information, offers optimal performance, stable version."),
	QWEN_VL_MAX_LATEST("qwen-vl-max-latest",
			"Qwen multi-modal model, supports image and text information, offers optimal performance, latest version."),
	QWEN_AUDIO_TURBO("qwen-audio-turbo", "Qwen audio understanding model, stable version."),
	QWEN_AUDIO_TURBO_LATEST("qwen-audio-turbo-latest", "Qwen audio understanding model, latest version."),
	QWEN_MT_TURBO("qwen-mt-turbo", "Qwen turbo model for translation."),
	QWEN_MT_PLUS("qwen-mt-plus", "Qwen plus model for translation."),
	QWQ_PLUS("qwq-plus", "Qwen reasoning model, stable version."),
	QWQ_PLUS_LATEST("qwq-plus-latest", "Qwen reasoning model, latest version.");

	private final String name;

	private final String description;

	QwenModel(String name) {
		this(name, "");
	}

	QwenModel(String name, String description) {
		this.name = name;
		this.description = description;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

}

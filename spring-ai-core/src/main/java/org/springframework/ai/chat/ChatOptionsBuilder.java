package org.springframework.ai.chat;

public class ChatOptionsBuilder {

	private class ChatOptionsImpl implements ChatOptions {

		private Float temperature;

		private Float topP;

		private Integer topK;

		@Override
		public Float getTemperature() {
			return temperature;
		}

		@Override
		public void setTemperature(Float temperature) {
			this.temperature = temperature;
		}

		@Override
		public Float getTopP() {
			return topP;
		}

		@Override
		public void setTopP(Float topP) {
			this.topP = topP;
		}

		@Override
		public Integer getTopK() {
			return topK;
		}

		@Override
		public void setTopK(Integer topK) {
			this.topK = topK;
		}

	}

	private final ChatOptionsImpl options = new ChatOptionsImpl();

	private ChatOptionsBuilder() {
	}

	public static ChatOptionsBuilder builder() {
		return new ChatOptionsBuilder();
	}

	public ChatOptionsBuilder withTemperature(Float temperature) {
		options.setTemperature(temperature);
		return this;
	}

	public ChatOptionsBuilder withTopP(Float topP) {
		options.setTopP(topP);
		return this;
	}

	public ChatOptionsBuilder withTopK(Integer topK) {
		options.setTopK(topK);
		return this;
	}

	public ChatOptions build() {
		return options;
	}

}

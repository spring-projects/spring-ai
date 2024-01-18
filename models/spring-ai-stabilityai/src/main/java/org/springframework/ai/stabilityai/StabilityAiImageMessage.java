package org.springframework.ai.stabilityai;

import java.util.Objects;

public class StabilityAiImageMessage {

	private String text;

	private Float weight;

	public StabilityAiImageMessage(String text) {
		this.text = text;
	}

	public StabilityAiImageMessage(String text, Float weight) {
		this.text = text;
		this.weight = weight;
	}

	public String getText() {
		return text;
	}

	public Float getWeight() {
		return weight;
	}

	@Override
	public String toString() {
		return "StabilityAiImageMessage{" + "text='" + text + '\'' + ", weight=" + weight + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof StabilityAiImageMessage that))
			return false;
		return Objects.equals(text, that.text) && Objects.equals(weight, that.weight);
	}

	@Override
	public int hashCode() {
		return Objects.hash(text, weight);
	}

}

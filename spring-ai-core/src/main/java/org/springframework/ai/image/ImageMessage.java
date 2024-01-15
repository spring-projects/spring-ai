package org.springframework.ai.image;

import java.util.Objects;

public class ImageMessage {

	private String text;

	private Float weight;

	public ImageMessage(String text) {
		this.text = text;
	}

	public ImageMessage(String text, Float weight) {
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
		return "mageMessage{" + "text='" + text + '\'' + ", weight=" + weight + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ImageMessage that))
			return false;
		return Objects.equals(text, that.text) && Objects.equals(weight, that.weight);
	}

	@Override
	public int hashCode() {
		return Objects.hash(text, weight);
	}

}

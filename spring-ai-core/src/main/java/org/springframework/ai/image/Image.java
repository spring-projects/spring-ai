package org.springframework.ai.image;

import java.util.Objects;

public class Image {

	/**
	 * The URL where the image can be accessed.
	 */
	private String url;

	/**
	 * Base64 encoded image string.
	 */
	private String b64Json;

	public Image(String url, String b64Json) {
		this.url = url;
		this.b64Json = b64Json;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getB64Json() {
		return b64Json;
	}

	public void setB64Json(String b64Json) {
		this.b64Json = b64Json;
	}

	@Override
	public String toString() {
		return "Image{" + "url='" + url + '\'' + ", b64Json='" + b64Json + '\'' + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Image image))
			return false;
		return Objects.equals(url, image.url) && Objects.equals(b64Json, image.b64Json);
	}

	@Override
	public int hashCode() {
		return Objects.hash(url, b64Json);
	}

}

/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.stabilityai;

/**
 * Enum representing different styles for images.
 */
public enum StyleEnum {

	// @formatter:off
	THREE_D_MODEL("3d-model"),
	ANALOG_FILM("analog-film"),
	ANIME("anime"),
	CINEMATIC("cinematic"),
	COMIC_BOOK("comic-book"),
	DIGITAL_ART("digital-art"),
	ENHANCE("enhance"),
	FANTASY_ART("fantasy-art"),
	ISOMETRIC("isometric"),
	LINE_ART("line-art"),
	LOW_POLY("low-poly"),
	MODELING_COMPOUND("modeling-compound"),
	NEON_PUNK("neon-punk"),
	ORIGAMI("origami"),
	PHOTOGRAPHIC("photographic"),
	PIXEL_ART("pixel-art"),
	TILE_TEXTURE("tile-texture");
	// @formatter:on

	private final String text;

	StyleEnum(final String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return text;
	}

}
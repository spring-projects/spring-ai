/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public final class AudioPlayer {

	private AudioPlayer() {
		throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
	}

	public static void main(String[] args) throws Exception {
		play(new BufferedInputStream(new FileInputStream(args[0])));
	}

	public static void play(byte[] data) {
		play(new BufferedInputStream(new ByteArrayInputStream(data)));
	}

	public static void play(InputStream data) {

		try {
			try (AudioInputStream audio = AudioSystem.getAudioInputStream(data); Clip clip = AudioSystem.getClip()) {
				clip.open(audio);
				clip.start();
				// wait to start
				while (!clip.isRunning()) {
					Thread.sleep(100);
				}
				// wait to finish
				while (clip.isRunning()) {
					Thread.sleep(3000);
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

}

package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class YouTubeTest {

	private static final String ID = "dQw4w9WgXcQ";

	@Test
	void readsTheIdFromEveryCommonLink() {
		String[] links = {
			ID,
			"https://www.youtube.com/watch?v=" + ID,
			"https://www.youtube.com/watch?v=" + ID + "&t=42s&list=PL123",
			"https://www.youtube.com/watch?feature=share&v=" + ID,
			"youtube.com/watch?v=" + ID,
			"https://m.youtube.com/watch?v=" + ID,
			"https://youtu.be/" + ID,
			"https://youtu.be/" + ID + "?si=abc123",
			"https://www.youtube.com/shorts/" + ID,
			"https://www.youtube.com/embed/" + ID,
			"https://www.youtube.com/live/" + ID,
			"https://www.youtube-nocookie.com/embed/" + ID,
			"  https://youtu.be/" + ID + "  ",
		};
		for (String link : links) {
			assertThat(YouTube.parseId(link)).as(link).contains(ID);
		}
	}

	@Test
	void rejectsThingsThatArentYouTubeVideos() {
		String[] bad = {
			null, "", "hello", "https://vimeo.com/12345678",
			"https://www.youtube.com/@grovesalons", "https://www.youtube.com/watch?v=short",
			"https://example.com/watch?v=" + ID,
		};
		for (String link : bad) {
			assertThat(YouTube.parseId(link)).as(String.valueOf(link)).isEmpty();
		}
	}

	@Test
	void usesThePrivacyEnhancedPlayer() {
		assertThat(YouTube.embedUrl(ID)).startsWith("https://www.youtube-nocookie.com/embed/" + ID);
		assertThat(YouTube.thumbnailUrl(ID)).isEqualTo("https://i.ytimg.com/vi/" + ID + "/hqdefault.jpg");
	}
}

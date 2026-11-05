package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.grove.Domain.VideoSource;

class SocialVideoTest {

	private static final String CODE = "C0dE_ab-12X";
	private static final String TIKTOK = "7234567890123456789";

	@Test
	void readsInstagramPostsAndReels() {
		for (String link : new String[] {
			"https://www.instagram.com/p/" + CODE + "/",
			"https://www.instagram.com/reel/" + CODE + "/?igsh=abc123",
			"https://www.instagram.com/reels/" + CODE,
			"instagram.com/tv/" + CODE,
			"https://www.instagram.com/somestylist/reel/" + CODE + "/",
		}) {
			assertThat(SocialVideo.parse(link)).as(link).contains(new SocialVideo.Post(VideoSource.INSTAGRAM, CODE));
		}
	}

	@Test
	void readsTikTokVideos() {
		for (String link : new String[] {
			"https://www.tiktok.com/@somestylist/video/" + TIKTOK + "?is_from_webapp=1",
			"tiktok.com/@somestylist/video/" + TIKTOK,
			"https://m.tiktok.com/v/" + TIKTOK + ".html",
			"https://www.tiktok.com/embed/v2/" + TIKTOK,
			"https://www.tiktok.com/player/v1/" + TIKTOK,
		}) {
			assertThat(SocialVideo.parse(link)).as(link).contains(new SocialVideo.Post(VideoSource.TIKTOK, TIKTOK));
		}
	}

	@Test
	void rejectsProfilesAndOtherSites() {
		for (String link : new String[] {
			null, "", "https://www.instagram.com/somestylist/", "https://www.tiktok.com/@somestylist",
			"https://www.tiktok.com/@somestylist/video/123", "https://vimeo.com/123", "https://evilinstagram.com/p/" + CODE,
		}) {
			assertThat(SocialVideo.parse(link)).as(String.valueOf(link)).isEmpty();
		}
	}

	@Test
	void knowsTikTokShortLinks() {
		assertThat(SocialVideo.isTikTokShortLink("https://vm.tiktok.com/ZMabc123/")).isTrue();
		assertThat(SocialVideo.isTikTokShortLink("https://www.tiktok.com/t/ZTabc123/")).isTrue();
		assertThat(SocialVideo.isTikTokShortLink("https://www.tiktok.com/@a/video/" + TIKTOK)).isFalse();
	}

	@Test
	void usesEachPlatformsOwnPlayer() {
		assertThat(SocialVideo.embedUrl(new SocialVideo.Post(VideoSource.INSTAGRAM, CODE)))
			.isEqualTo("https://www.instagram.com/p/" + CODE + "/embed/");
		assertThat(SocialVideo.embedUrl(new SocialVideo.Post(VideoSource.TIKTOK, TIKTOK)))
			.startsWith("https://www.tiktok.com/player/v1/" + TIKTOK);
	}
}

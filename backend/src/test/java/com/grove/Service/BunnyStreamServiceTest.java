package com.grove.Service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import com.grove.Domain.Dtos.UploadTicket;

class BunnyStreamServiceTest {

	private final BunnyStreamService bunny =
		new BunnyStreamService(new RestTemplate(), "12345", "api-key", "token-key", "vz-test.b-cdn.net");

	@Test
	void uploadSignatureFollowsBunnyFormula() {
		UploadTicket t = bunny.uploadTicket(7L, "abc-guid");
		String expected = BunnyStreamService.sha256Hex("12345" + "api-key" + t.expires() + "abc-guid");
		assertThat(t.signature()).isEqualTo(expected).hasSize(64);
		assertThat(t.libraryId()).isEqualTo("12345");
		assertThat(t.endpoint()).isEqualTo("https://video.bunnycdn.com/tusupload");
	}

	@Test
	void embedUrlIsSignedWhenTokenKeySet() {
		String url = bunny.embedUrl("abc-guid");
		assertThat(url).startsWith("https://iframe.mediadelivery.net/embed/12345/abc-guid?token=");
		long expires = Long.parseLong(url.replaceAll(".*expires=(\\d+).*", "$1"));
		String token = url.replaceAll(".*token=([0-9a-f]+).*", "$1");
		assertThat(token).isEqualTo(BunnyStreamService.sha256Hex("token-key" + "abc-guid" + expires));
	}

	@Test
	void embedUrlUnsignedWithoutTokenKey() {
		BunnyStreamService open = new BunnyStreamService(new RestTemplate(), "12345", "k", "", "");
		assertThat(open.embedUrl("g")).isEqualTo("https://iframe.mediadelivery.net/embed/12345/g?preload=true");
		assertThat(open.thumbnailUrl("g")).isNull();
	}
}

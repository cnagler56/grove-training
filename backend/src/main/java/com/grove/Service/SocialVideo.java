package com.grove.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;

import com.grove.Domain.VideoSource;

/**
 * Instagram and TikTok link handling for training videos that play in the
 * platform's own embedded player. No API key: the post's code (Instagram) or
 * numeric id (TikTok) from the share link is all the player needs. The post
 * has to be public to play.
 */
public final class SocialVideo {

	private SocialVideo() {}

	/** A recognized post: where it lives and its code or id. */
	public record Post(VideoSource source, String id) {}

	private static final Pattern INSTAGRAM_CODE = Pattern.compile("[A-Za-z0-9_-]{5,40}");
	private static final Pattern TIKTOK_ID = Pattern.compile("\\d{15,25}");

	/**
	 * The post from an Instagram link (instagram.com/p/CODE, /reel/CODE, /reels/CODE,
	 * /tv/CODE, also with a username in front) or a TikTok link (tiktok.com/@name/video/ID,
	 * /embed/v2/ID, /player/v1/ID, m.tiktok.com/v/ID.html). Empty if it isn't one.
	 * TikTok's short links (vm.tiktok.com/..., tiktok.com/t/...) don't carry the id:
	 * see {@link #isTikTokShortLink}.
	 */
	public static Optional<Post> parse(String input) {
		URI uri = toUri(input);
		if (uri == null) return Optional.empty();
		String host = host(uri);
		String[] parts = uri.getPath() == null ? new String[0] : uri.getPath().split("/");
		if (host.equals("instagram.com") || host.equals("instagr.am")) {
			for (int i = 0; i < parts.length - 1; i++) {
				if (parts[i].equals("p") || parts[i].equals("reel") || parts[i].equals("reels") || parts[i].equals("tv")) {
					String code = parts[i + 1];
					return INSTAGRAM_CODE.matcher(code).matches()
						? Optional.of(new Post(VideoSource.INSTAGRAM, code)) : Optional.empty();
				}
			}
		} else if (host.equals("tiktok.com")) {
			for (int i = 0; i < parts.length - 1; i++) {
				String next = parts[i + 1].replaceFirst("\\.html$", "");
				boolean marker = parts[i].equals("video") || parts[i].equals("v")
					|| (i > 0 && parts[i - 1].equals("embed")) || (i > 0 && parts[i - 1].equals("player"));
				if (marker && TIKTOK_ID.matcher(next).matches()) return Optional.of(new Post(VideoSource.TIKTOK, next));
			}
		}
		return Optional.empty();
	}

	/** A TikTok short link, which only redirects to the full one. */
	public static boolean isTikTokShortLink(String input) {
		URI uri = toUri(input);
		if (uri == null) return false;
		String host = host(uri);
		String path = uri.getPath() == null ? "" : uri.getPath();
		return host.equals("vm.tiktok.com") || host.equals("vt.tiktok.com")
			|| (host.equals("tiktok.com") && path.startsWith("/t/"));
	}

	/** The platform's own embedded player for the post. */
	public static String embedUrl(Post post) {
		return switch (post.source()) {
			case INSTAGRAM -> "https://www.instagram.com/p/" + post.id() + "/embed/";
			case TIKTOK -> "https://www.tiktok.com/player/v1/" + post.id() + "?rel=0";
			default -> throw new IllegalArgumentException("Not an Instagram or TikTok post");
		};
	}

	private static URI toUri(String input) {
		if (input == null || input.isBlank()) return null;
		String s = input.trim();
		if (!s.matches("(?i)^https?://.*")) s = "https://" + s;
		try {
			URI uri = new URI(s);
			return uri.getHost() == null ? null : uri;
		} catch (URISyntaxException e) {
			return null;
		}
	}

	private static String host(URI uri) {
		return uri.getHost().toLowerCase().replaceFirst("^(www|m)\\.", "");
	}
}

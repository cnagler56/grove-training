package com.grove.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * YouTube link handling for training videos that live on YouTube instead of
 * Bunny. No API key: we only need the 11-character video id, which every
 * share-link format carries.
 */
public final class YouTube {

	private YouTube() {}

	private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{11}");

	/**
	 * The video id from any common link: youtube.com/watch?v=ID, youtu.be/ID,
	 * youtube.com/shorts/ID, /embed/ID, /live/ID, m.youtube.com and
	 * youtube-nocookie.com variants, or a bare id. Empty if it isn't one.
	 */
	public static Optional<String> parseId(String input) {
		if (input == null) return Optional.empty();
		String s = input.trim();
		if (ID.matcher(s).matches()) return Optional.of(s);
		if (!s.matches("(?i)^https?://.*")) s = "https://" + s;
		URI uri;
		try {
			uri = new URI(s);
		} catch (URISyntaxException e) {
			return Optional.empty();
		}
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase().replaceFirst("^(www|m|music)\\.", "");
		String path = uri.getPath() == null ? "" : uri.getPath();
		String candidate = null;
		if (host.equals("youtu.be")) {
			candidate = path.replaceFirst("^/", "").split("/")[0];
		} else if (host.equals("youtube.com") || host.equals("youtube-nocookie.com")) {
			if (path.equals("/watch")) {
				candidate = queryParam(uri.getRawQuery(), "v");
			} else {
				String[] parts = path.split("/");
				if (parts.length >= 3 && (parts[1].equals("shorts") || parts[1].equals("embed")
						|| parts[1].equals("live") || parts[1].equals("v"))) {
					candidate = parts[2];
				}
			}
		}
		return candidate != null && ID.matcher(candidate).matches() ? Optional.of(candidate) : Optional.empty();
	}

	/** Privacy-enhanced player: no YouTube cookies until someone presses play. */
	public static String embedUrl(String id) {
		return "https://www.youtube-nocookie.com/embed/" + id + "?rel=0&modestbranding=1";
	}

	public static String thumbnailUrl(String id) {
		return "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
	}

	private static String queryParam(String query, String name) {
		if (query == null) return null;
		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq > 0 && pair.substring(0, eq).equals(name)) return pair.substring(eq + 1);
		}
		return null;
	}
}

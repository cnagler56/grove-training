package com.grove.Domain;

/** Where a training video plays from. */
public enum VideoSource {
	/** Uploaded to our Bunny Stream library; only signed-in staff can play it. */
	BUNNY,
	/** A link to a public or unlisted YouTube video. */
	YOUTUBE
}

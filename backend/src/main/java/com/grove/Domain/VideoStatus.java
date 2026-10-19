package com.grove.Domain;

/**
 * Our simplified view of Bunny Stream's numeric status codes:
 * 0 created → UPLOADING; 1 uploaded / 2 processing / 3 transcoding → PROCESSING;
 * 4 finished, 7–8 JIT-playable → READY; 5 error / 6 upload failed → FAILED.
 */
public enum VideoStatus {
	UPLOADING,
	PROCESSING,
	READY,
	FAILED;

	public static VideoStatus fromBunny(int code) {
		return switch (code) {
			case 0 -> UPLOADING;
			case 1, 2, 3 -> PROCESSING;
			case 4, 7, 8 -> READY;
			default -> FAILED;
		};
	}

	public boolean isSettled() {
		return this == READY || this == FAILED;
	}
}

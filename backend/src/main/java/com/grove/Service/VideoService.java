package com.grove.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.CompletionReport;
import com.grove.Domain.Dtos.CompletionRow;
import com.grove.Domain.Dtos.UploadTicket;
import com.grove.Domain.Dtos.VideoDetail;
import com.grove.Domain.Dtos.VideoRequest;
import com.grove.Domain.Dtos.VideoSummary;
import com.grove.Domain.Dtos.YouTubeRequest;
import com.grove.Domain.User;
import com.grove.Domain.Video;
import com.grove.Domain.VideoCompletion;
import com.grove.Domain.VideoSource;
import com.grove.Domain.VideoStatus;
import com.grove.Repository.UserRepository;
import com.grove.Repository.VideoCompletionRepository;
import com.grove.Repository.VideoRepository;

/**
 * The training library: listing, admin create/edit/delete, and watch tracking.
 *
 * Videos that are still uploading/processing are re-checked against Bunny when
 * someone looks at them, so there's no background polling job to keep the
 * server awake.
 */
@Service
public class VideoService {

	private final VideoRepository videoRepo;
	private final VideoCompletionRepository completionRepo;
	private final UserRepository userRepo;
	private final BunnyStreamService bunny;

	public VideoService(VideoRepository videoRepo, VideoCompletionRepository completionRepo,
			UserRepository userRepo, BunnyStreamService bunny) {
		this.videoRepo = videoRepo;
		this.completionRepo = completionRepo;
		this.userRepo = userRepo;
		this.bunny = bunny;
	}

	/** Staff see READY videos; managers also see ones still in progress; customers see none. */
	public List<VideoSummary> list(User viewer) {
		if (!viewer.getRoles().isStaff()) return List.of();
		List<Video> videos = videoRepo.findAllByOrderByCreatedAtDesc();
		if (viewer.isAdmin()) videos.forEach(this::refreshStatus);
		Set<Long> done = completedIds(viewer);
		return videos.stream()
			.filter(v -> viewer.isAdmin() || v.getStatus() == VideoStatus.READY)
			.map(v -> toSummary(v, done.contains(v.getId())))
			.toList();
	}

	public VideoDetail detail(User viewer, Long id) {
		Video v = find(viewer, id);
		refreshStatus(v);
		if (!viewer.isAdmin() && v.getStatus() != VideoStatus.READY) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
		}
		boolean completed = completionRepo.findByUserIdAndVideoId(viewer.getUserId(), id).isPresent();
		return new VideoDetail(toSummary(v, completed), embedUrl(v));
	}

	@Transactional
	public void setCompleted(User viewer, Long id, boolean completed) {
		Video v = find(viewer, id);
		var existing = completionRepo.findByUserIdAndVideoId(viewer.getUserId(), v.getId());
		if (completed && existing.isEmpty()) {
			VideoCompletion c = new VideoCompletion();
			c.setUserId(viewer.getUserId());
			c.setVideoId(v.getId());
			c.setCompletedAt(LocalDateTime.now());
			completionRepo.save(c);
		} else if (!completed) {
			existing.ifPresent(completionRepo::delete);
		}
	}

	// ---- Manager -------------------------------------------------------------

	/** Create the video record + its empty Bunny video, and return an upload ticket. */
	public UploadTicket create(User admin, VideoRequest req) {
		String title = requireTitle(req);
		String guid = bunny.createVideo(title);
		Video v = new Video();
		v.setTitle(title);
		v.setDescription(blankToNull(req.description()));
		v.setCategory(blankToNull(req.category()));
		v.setBunnyVideoId(guid);
		v.setStatus(VideoStatus.UPLOADING);
		v.setCreatedBy(admin.getUserId());
		v.setCreatedAt(LocalDateTime.now());
		v = videoRepo.save(v);
		return bunny.uploadTicket(v.getId(), guid);
	}

	/**
	 * Add a YouTube video by link. It's live right away: nothing to upload or
	 * process. The video must be public or unlisted on YouTube to play here.
	 */
	public VideoSummary createYouTube(User admin, YouTubeRequest req) {
		String youtubeId = YouTube.parseId(req == null ? null : req.url())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"That doesn't look like a YouTube link. Copy it from the Share button on YouTube."));
		String title = requireTitle(new VideoRequest(req.title(), req.description(), req.category()));
		Video v = new Video();
		v.setTitle(title);
		v.setDescription(blankToNull(req.description()));
		v.setCategory(blankToNull(req.category()));
		v.setSource(VideoSource.YOUTUBE);
		v.setYoutubeId(youtubeId);
		v.setStatus(VideoStatus.READY);
		v.setCreatedBy(admin.getUserId());
		v.setCreatedAt(LocalDateTime.now());
		return toSummary(videoRepo.save(v), false);
	}

	/** A fresh ticket to retry or resume an upload. */
	public UploadTicket uploadTicket(Long id) {
		Video v = find(id);
		if (v.getSource() == VideoSource.YOUTUBE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "YouTube videos don't need uploading");
		}
		if (v.getStatus() == VideoStatus.READY) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This video is already uploaded");
		}
		return bunny.uploadTicket(v.getId(), v.getBunnyVideoId());
	}

	public VideoSummary update(Long id, VideoRequest req) {
		Video v = find(id);
		v.setTitle(requireTitle(req));
		v.setDescription(blankToNull(req.description()));
		v.setCategory(blankToNull(req.category()));
		return toSummary(videoRepo.save(v), false);
	}

	@Transactional
	public void delete(Long id) {
		Video v = find(id);
		bunny.deleteVideo(v.getBunnyVideoId());
		completionRepo.deleteByVideoId(v.getId());
		videoRepo.delete(v);
	}

	/** Every active employee, with when (or whether) they watched this video. */
	public CompletionReport completions(Long id) {
		Video v = find(id);
		Map<Long, LocalDateTime> watched = completionRepo.findByVideoId(v.getId()).stream()
			.collect(Collectors.toMap(VideoCompletion::getUserId, VideoCompletion::getCompletedAt, (a, b) -> a));
		List<CompletionRow> rows = userRepo.findAll().stream()
			.filter(u -> u.isActive() && u.getRoles().isStaff())
			.map(u -> new CompletionRow(u.getUserId(), displayName(u), u.getEmail(), watched.get(u.getUserId())))
			.sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
			.toList();
		return new CompletionReport(v.getId(), rows);
	}

	/** Distinct non-empty categories, for the upload form's suggestions. */
	public List<String> categories() {
		return videoRepo.findAll().stream()
			.map(Video::getCategory).filter(c -> c != null && !c.isBlank())
			.collect(Collectors.toMap(String::toLowerCase, Function.identity(), (a, b) -> a))
			.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
	}

	// ---- helpers -------------------------------------------------------------

	/** Pull the latest status from Bunny for a video that isn't finished yet. Never throws. */
	private void refreshStatus(Video v) {
		if (v.getStatus().isSettled() || v.getBunnyVideoId() == null || !bunny.isConfigured()) return;
		try {
			BunnyStreamService.BunnyVideo b = bunny.getVideo(v.getBunnyVideoId());
			VideoStatus next = VideoStatus.fromBunny(b.status());
			Integer length = b.lengthSeconds() > 0 ? b.lengthSeconds() : v.getDurationSeconds();
			if (next != v.getStatus() || !java.util.Objects.equals(length, v.getDurationSeconds())) {
				v.setStatus(next);
				v.setDurationSeconds(length);
				videoRepo.save(v);
			}
		} catch (ResponseStatusException e) {
			// Bunny unreachable — keep showing the last known status.
		}
	}

	private Set<Long> completedIds(User viewer) {
		return completionRepo.findByUserId(viewer.getUserId()).stream()
			.map(VideoCompletion::getVideoId).collect(Collectors.toSet());
	}

	/** Player URL: YouTube's privacy-enhanced player, or a signed Bunny embed once processing is done. */
	private String embedUrl(Video v) {
		if (v.getSource() == VideoSource.YOUTUBE) return YouTube.embedUrl(v.getYoutubeId());
		return v.getStatus() == VideoStatus.READY ? bunny.embedUrl(v.getBunnyVideoId()) : null;
	}

	private VideoSummary toSummary(Video v, boolean completed) {
		String thumb = v.getSource() == VideoSource.YOUTUBE
			? YouTube.thumbnailUrl(v.getYoutubeId())
			: v.getStatus() == VideoStatus.READY ? bunny.thumbnailUrl(v.getBunnyVideoId()) : null;
		return new VideoSummary(v.getId(), v.getTitle(), v.getDescription(), v.getCategory(), v.getSource(), v.getStatus(),
			v.getDurationSeconds(), thumb, completed, v.getCreatedAt());
	}

	/** Training videos are staff-only: a customer gets the same 404 as a missing video. */
	private Video find(User viewer, Long id) {
		if (!viewer.getRoles().isStaff()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found");
		return find(id);
	}

	private Video find(Long id) {
		return videoRepo.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
	}

	private static String requireTitle(VideoRequest req) {
		if (req == null || req.title() == null || req.title().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give the video a title");
		}
		return req.title().trim();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static String displayName(User u) {
		String name = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
			+ (u.getLastName() == null ? "" : u.getLastName())).trim();
		return name.isEmpty() ? u.getEmail() : name;
	}
}

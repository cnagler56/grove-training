package com.grove.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Dtos.TeamCommentView;
import com.grove.Domain.Dtos.TeamFeed;
import com.grove.Domain.Dtos.TeamPostInput;
import com.grove.Domain.Dtos.TeamPostView;
import com.grove.Domain.Permission;
import com.grove.Domain.TeamPost;
import com.grove.Domain.TeamPostComment;
import com.grove.Domain.TeamPostImage;
import com.grove.Domain.TeamPostLike;
import com.grove.Domain.User;
import com.grove.Repository.TeamPostCommentRepository;
import com.grove.Repository.TeamPostImageRepository;
import com.grove.Repository.TeamPostLikeRepository;
import com.grove.Repository.TeamPostRepository;
import com.grove.Repository.UserRepository;

/**
 * The Team board on the staff Home page: posts with photos, comments, likes,
 * and pinned notices. Staff only. The owner picks which roles can post on
 * Settings; everyone on staff can comment and like. People can delete their own
 * posts and comments, and managers and up can delete any or pin posts.
 */
@Service
public class TeamBoardService {

	private static final int PAGE_SIZE = 10;
	private static final int MAX_BODY = 5000;
	private static final int MAX_COMMENT = 1000;
	private static final int MAX_IMAGES = 6;
	private static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;

	private final TeamPostRepository posts;
	private final TeamPostImageRepository images;
	private final TeamPostCommentRepository comments;
	private final TeamPostLikeRepository likes;
	private final UserRepository users;
	private final PermissionService perms;
	private final PushService push;
	private final ZoneId zone;

	public TeamBoardService(TeamPostRepository posts, TeamPostImageRepository images, TeamPostCommentRepository comments,
			TeamPostLikeRepository likes, UserRepository users, PermissionService perms, PushService push,
			@Value("${app.time-zone:America/Chicago}") String zone) {
		this.posts = posts;
		this.images = images;
		this.comments = comments;
		this.likes = likes;
		this.users = users;
		this.perms = perms;
		this.push = push;
		this.zone = ZoneId.of(zone);
	}

	/** One page of the board, pinned posts first, then newest. */
	public TeamFeed feed(User viewer, int page) {
		requireStaff(viewer);
		Page<TeamPost> result = posts.feed(PageRequest.of(Math.max(page, 0), PAGE_SIZE));
		List<TeamPost> list = result.getContent();
		List<Long> ids = list.stream().map(TeamPost::getId).toList();
		if (ids.isEmpty()) return new TeamFeed(List.of(), false);

		Map<Long, List<Long>> imageIds = images.refs(ids).stream().collect(Collectors.groupingBy(
			TeamPostImageRepository.ImageRef::getPostId,
			Collectors.mapping(TeamPostImageRepository.ImageRef::getId, Collectors.toList())));
		Map<Long, List<TeamPostComment>> commentsByPost = comments.findByPostIdInOrderByCreatedAtAscIdAsc(ids).stream()
			.collect(Collectors.groupingBy(TeamPostComment::getPostId));
		Map<Long, List<TeamPostLike>> likesByPost = likes.findByPostIdIn(ids).stream()
			.collect(Collectors.groupingBy(TeamPostLike::getPostId));
		Map<Long, User> people = users.findAll().stream().collect(Collectors.toMap(User::getUserId, Function.identity()));

		List<TeamPostView> views = list.stream().map(p -> {
			List<TeamPostLike> postLikes = likesByPost.getOrDefault(p.getId(), List.of());
			Set<Long> likers = postLikes.stream().map(TeamPostLike::getUserId).collect(Collectors.toSet());
			List<TeamCommentView> postComments = commentsByPost.getOrDefault(p.getId(), List.of()).stream()
				.map(c -> new TeamCommentView(c.getId(), nameOf(people, c.getAuthorId()), c.getBody(), c.getCreatedAt(),
					canRemove(viewer, c.getAuthorId())))
				.toList();
			return new TeamPostView(p.getId(), nameOf(people, p.getAuthorId()), p.getBody(), p.isPinned(), p.getCreatedAt(),
				imageIds.getOrDefault(p.getId(), List.of()), likers.size(), likers.contains(viewer.getUserId()),
				postComments, canRemove(viewer, p.getAuthorId()));
		}).toList();
		return new TeamFeed(views, result.hasNext());
	}

	/** Start a post. Text is optional only when photos are about to follow. */
	public Long create(User author, TeamPostInput input) {
		perms.require(author, Permission.POST_UPDATES, "You don't have permission to post on the team board");
		String body = input == null ? null : clean(input.body());
		boolean withPhotos = input != null && input.withPhotos();
		if (body == null && !withPhotos) throw bad("Write something or add a photo");
		if (body != null && body.length() > MAX_BODY) throw bad("Posts are limited to " + MAX_BODY + " characters");
		TeamPost p = new TeamPost();
		p.setAuthorId(author.getUserId());
		p.setBody(body);
		p.setCreatedAt(LocalDateTime.now(zone));
		Long id = posts.save(p).getId();
		push.notify(push.staffExcept(author.getUserId()), displayName(author) + " posted on the team board",
			body == null ? "Shared photos" : preview(body), "/home");
		return id;
	}

	/** Add one photo to your own post. Only JPEGs: the browser converts and shrinks them first. */
	public void addImage(User author, Long postId, byte[] data) {
		TeamPost p = find(postId);
		if (!p.getAuthorId().equals(author.getUserId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only add photos to your own posts");
		}
		if (data == null || data.length < 3 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8 || (data[2] & 0xFF) != 0xFF) {
			throw bad("That photo couldn't be read");
		}
		if (data.length > MAX_IMAGE_BYTES) throw bad("That photo is too large");
		if (images.countByPostId(postId) >= MAX_IMAGES) throw bad("A post can have up to " + MAX_IMAGES + " photos");
		images.save(new TeamPostImage(postId, data));
	}

	public byte[] image(User viewer, Long imageId) {
		requireStaff(viewer);
		return images.findById(imageId).map(TeamPostImage::getData)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
	}

	@Transactional
	public void delete(User viewer, Long postId) {
		TeamPost p = find(postId);
		if (!canRemove(viewer, p.getAuthorId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own posts");
		}
		images.deleteByPostId(postId);
		comments.deleteByPostId(postId);
		likes.deleteByPostId(postId);
		posts.delete(p);
	}

	public void setPinned(User viewer, Long postId, boolean pinned) {
		if (!viewer.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only managers and up can pin posts");
		TeamPost p = find(postId);
		p.setPinned(pinned);
		posts.save(p);
	}

	public void comment(User author, Long postId, String text) {
		requireStaff(author);
		TeamPost post = find(postId);
		String body = clean(text);
		if (body == null) throw bad("Write a comment first");
		if (body.length() > MAX_COMMENT) throw bad("Comments are limited to " + MAX_COMMENT + " characters");
		comments.save(new TeamPostComment(postId, author.getUserId(), body, LocalDateTime.now(zone)));
		if (!post.getAuthorId().equals(author.getUserId())) {
			push.notify(List.of(post.getAuthorId()), displayName(author) + " commented on your post", preview(body), "/home");
		}
	}

	public void deleteComment(User viewer, Long commentId) {
		TeamPostComment c = comments.findById(commentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
		if (!canRemove(viewer, c.getAuthorId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only delete your own comments");
		}
		comments.delete(c);
	}

	@Transactional
	public void setLiked(User viewer, Long postId, boolean liked) {
		requireStaff(viewer);
		find(postId);
		if (!liked) {
			likes.unlike(postId, viewer.getUserId());
		} else if (!likes.existsByPostIdAndUserId(postId, viewer.getUserId())) {
			likes.save(new TeamPostLike(postId, viewer.getUserId()));
		}
	}

	private boolean canRemove(User viewer, Long authorId) {
		return viewer.isAdmin() || viewer.getUserId().equals(authorId);
	}

	private static void requireStaff(User viewer) {
		if (!viewer.getRoles().isStaff()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The team board is for staff");
	}

	private TeamPost find(Long id) {
		return posts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
	}

	private static String nameOf(Map<Long, User> people, Long id) {
		User u = people.get(id);
		if (u == null) return "Former employee";
		String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
		return n.isEmpty() ? u.getEmail() : n;
	}

	private static String displayName(User u) {
		return nameOf(Map.of(u.getUserId(), u), u.getUserId());
	}

	private static String preview(String text) {
		return text.length() <= 140 ? text : text.substring(0, 137) + "...";
	}

	private static String clean(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}

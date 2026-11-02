package com.grove.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.grove.Domain.Role;
import com.grove.Domain.TodoAssignment;
import com.grove.Domain.TodoItem;
import com.grove.Domain.User;
import com.grove.Repository.TodoAssignmentRepository;
import com.grove.Repository.TodoItemRepository;
import com.grove.Repository.UserRepository;

/**
 * To-do checklists. Owners and general managers assign to managers and receptionists;
 * managers assign to receptionists. Each person checks off their own copy, and whoever
 * assigned it sees when it was done and by whom.
 */
@Service
public class TodoService {

	/** Finished items stay on your own list this long, so a mistaken check can be undone. */
	private static final int DONE_DAYS_ON_MY_LIST = 7;
	/** Finished items stay on the assigner's list this long. */
	private static final int DONE_DAYS_ON_ASSIGNED_LIST = 30;
	private static final int MAX_TEXT = 200;

	public record TodoView(Long id, String text, Long assigneeId, String assigneeName, String assignedByName,
		LocalDateTime assignedAt, LocalDateTime completedAt) {}

	public record Person(Long userId, String name, String role) {}

	public record SavedItem(Long id, String text) {}

	/** What the assign form offers. */
	public record AssignOptions(List<Person> people, List<SavedItem> items) {}

	public record AssignRequest(String text, List<Long> userIds) {}

	private final TodoAssignmentRepository assignments;
	private final TodoItemRepository items;
	private final UserRepository users;
	private final ZoneId zone;

	public TodoService(TodoAssignmentRepository assignments, TodoItemRepository items, UserRepository users,
			@Value("${app.time-zone:America/Chicago}") String zone) {
		this.assignments = assignments;
		this.items = items;
		this.users = users;
		this.zone = ZoneId.of(zone);
	}

	/** Can {@code assigner} put something on {@code assignee}'s list? */
	public static boolean canAssign(Role assigner, Role assignee) {
		return switch (assigner) {
			case OWNER, GENERAL_MANAGER -> assignee == Role.MANAGER || assignee == Role.RECEPTIONIST;
			case MANAGER -> assignee == Role.RECEPTIONIST;
			default -> false;
		};
	}

	/** The viewer's own list: what's left, oldest first, then what they finished lately. */
	public List<TodoView> mine(User viewer) {
		return openThenRecentlyDone(assignments.findByAssigneeIdOrderByAssignedAtAsc(viewer.getUserId()), DONE_DAYS_ON_MY_LIST);
	}

	/** What the viewer assigned: what's still open, then what was finished lately, latest first. */
	public List<TodoView> assignedBy(User viewer) {
		return openThenRecentlyDone(assignments.findByAssignedByOrderByAssignedAtDesc(viewer.getUserId()), DONE_DAYS_ON_ASSIGNED_LIST);
	}

	private List<TodoView> openThenRecentlyDone(List<TodoAssignment> all, int doneDays) {
		LocalDateTime cutoff = LocalDateTime.now(zone).minusDays(doneDays);
		List<TodoView> list = new ArrayList<>(all.stream().filter(t -> t.getCompletedAt() == null).map(this::view).toList());
		list.addAll(all.stream()
			.filter(t -> t.getCompletedAt() != null && t.getCompletedAt().isAfter(cutoff))
			.sorted(Comparator.comparing(TodoAssignment::getCompletedAt).reversed())
			.map(this::view).toList());
		return list;
	}

	public AssignOptions options(User viewer) {
		requireAssigner(viewer);
		List<Person> people = users.findAll().stream()
			.filter(u -> u.isActive() && canAssign(viewer.getRoles(), u.getRoles()))
			.map(u -> new Person(u.getUserId(), name(u), u.getRoles().name()))
			.sorted(Comparator.comparing((Person p) -> p.name().toLowerCase()))
			.toList();
		List<SavedItem> saved = items.findAll().stream()
			.map(i -> new SavedItem(i.getId(), i.getText()))
			.sorted(Comparator.comparing((SavedItem i) -> i.text().toLowerCase()))
			.toList();
		return new AssignOptions(people, saved);
	}

	/** Puts one item on each person's list and saves it for the dropdown. All or nothing. */
	@Transactional
	public List<TodoView> assign(User assigner, AssignRequest body) {
		requireAssigner(assigner);
		String typed = body == null || body.text() == null ? "" : body.text().trim();
		if (typed.isEmpty()) throw bad("Pick or type a to-do");
		if (typed.length() > MAX_TEXT) typed = typed.substring(0, MAX_TEXT);
		if (body.userIds() == null || body.userIds().isEmpty()) throw bad("Pick at least one person");

		Set<Long> ids = new LinkedHashSet<>(body.userIds());
		List<User> people = new ArrayList<>();
		for (Long id : ids) {
			User u = id == null ? null : users.findById(id).filter(User::isActive).orElse(null);
			if (u == null) throw bad("One of those people isn't on the team anymore");
			if (!canAssign(assigner.getRoles(), u.getRoles())) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can't assign to-dos to " + name(u));
			}
			people.add(u);
		}

		String text = saved(typed).getText();
		LocalDateTime now = LocalDateTime.now(zone);
		// Someone who still has this one open doesn't need it twice.
		List<TodoAssignment> created = people.stream()
			.filter(u -> !assignments.existsByAssigneeIdAndTextAndCompletedAtIsNull(u.getUserId(), text))
			.map(u -> {
				TodoAssignment t = new TodoAssignment();
				t.setText(text);
				t.setAssigneeId(u.getUserId());
				t.setAssignedBy(assigner.getUserId());
				t.setAssignedAt(now);
				return assignments.save(t);
			}).toList();
		if (created.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That's already on their list and not done yet");
		}
		return created.stream().map(this::view).toList();
	}

	/** Check off or un-check your own to-do. No one else can. */
	public TodoView setDone(User viewer, Long id, boolean done) {
		TodoAssignment t = find(id);
		if (!t.getAssigneeId().equals(viewer.getUserId())) throw notFound();
		if (done && t.getCompletedAt() == null) t.setCompletedAt(LocalDateTime.now(zone));
		if (!done) t.setCompletedAt(null);
		return view(assignments.save(t));
	}

	/** Take a to-do back off someone's list. Only the person who assigned it can. */
	public void remove(User viewer, Long id) {
		TodoAssignment t = find(id);
		if (!t.getAssignedBy().equals(viewer.getUserId())) throw notFound();
		assignments.delete(t);
	}

	/** Take an item out of the dropdown. Lists it's already on keep it. */
	public void removeSavedItem(User viewer, Long id) {
		requireAssigner(viewer);
		items.delete(items.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "That item isn't saved anymore")));
	}

	private TodoItem saved(String text) {
		return items.findFirstByTextIgnoreCase(text).orElseGet(() -> {
			TodoItem i = new TodoItem();
			i.setText(text);
			i.setCreatedAt(LocalDateTime.now(zone));
			return items.save(i);
		});
	}

	private static void requireAssigner(User viewer) {
		if (!viewer.isAdmin()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners, general managers, and managers can assign to-dos");
		}
	}

	private TodoAssignment find(Long id) {
		return assignments.findById(id).orElseThrow(TodoService::notFound);
	}

	private TodoView view(TodoAssignment t) {
		return new TodoView(t.getId(), t.getText(), t.getAssigneeId(), nameOf(t.getAssigneeId()), nameOf(t.getAssignedBy()),
			t.getAssignedAt(), t.getCompletedAt());
	}

	private String nameOf(Long userId) {
		return users.findById(userId).map(TodoService::name).orElse("Former employee");
	}

	private static String name(User u) {
		String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
		return n.isEmpty() ? u.getEmail() : n;
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "That to-do isn't on the list anymore");
	}

	private static ResponseStatusException bad(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}

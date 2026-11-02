package com.grove.Controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Service.SessionService;
import com.grove.Service.TodoService;
import com.grove.Service.TodoService.AssignOptions;
import com.grove.Service.TodoService.AssignRequest;
import com.grove.Service.TodoService.TodoView;

/** The To-do tab: assigning checklist items, and checking off your own. */
@RestController
@RequestMapping("/api/todos")
public class TodoController {

	private final TodoService todos;
	private final SessionService sessions;

	public TodoController(TodoService todos, SessionService sessions) {
		this.todos = todos;
		this.sessions = sessions;
	}

	@GetMapping("/mine")
	public List<TodoView> mine(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return todos.mine(sessions.requireUser(token));
	}

	@PutMapping("/{id}/done")
	public TodoView done(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		return todos.setDone(sessions.requireUser(token), id, true);
	}

	@PutMapping("/{id}/not-done")
	public TodoView notDone(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		return todos.setDone(sessions.requireUser(token), id, false);
	}

	@GetMapping("/assigned")
	public List<TodoView> assigned(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return todos.assignedBy(sessions.requireUser(token));
	}

	@GetMapping("/assign-options")
	public AssignOptions options(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return todos.options(sessions.requireUser(token));
	}

	@PostMapping
	public ResponseEntity<List<TodoView>> assign(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@RequestBody AssignRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(todos.assign(sessions.requireUser(token), body));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> remove(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		todos.remove(sessions.requireUser(token), id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/items/{id}")
	public ResponseEntity<Void> removeSavedItem(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token,
			@PathVariable Long id) {
		todos.removeSavedItem(sessions.requireUser(token), id);
		return ResponseEntity.noContent().build();
	}
}

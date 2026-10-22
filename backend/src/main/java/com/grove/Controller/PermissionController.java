package com.grove.Controller;

import java.util.List;

import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.grove.Domain.Dtos.MyPermissions;
import com.grove.Domain.Dtos.PermissionRule;
import com.grove.Domain.Dtos.PermissionUpdate;
import com.grove.Service.PermissionService;
import com.grove.Service.SessionService;

/** What I'm allowed to do, and the owner's Settings page for who's allowed what. */
@RestController
public class PermissionController {

	private final PermissionService perms;
	private final SessionService sessions;

	public PermissionController(PermissionService perms, SessionService sessions) {
		this.perms = perms;
		this.sessions = sessions;
	}

	@GetMapping("/api/me/permissions")
	public MyPermissions mine(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		return perms.forUser(sessions.requireUser(token));
	}

	@GetMapping("/api/owner/permissions")
	public List<PermissionRule> rules(@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return perms.rules();
	}

	@PutMapping("/api/owner/permissions")
	public List<PermissionRule> update(@RequestBody PermissionUpdate body,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return perms.update(body.roles());
	}
}

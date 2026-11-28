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
import com.grove.Service.ManagerOnDutyService;
import com.grove.Service.PermissionService;
import com.grove.Service.SessionService;

/** What I'm allowed to do, and the owner's Settings page for who's allowed what. */
@RestController
public class PermissionController {

	private final PermissionService perms;
	private final SessionService sessions;
	private final ManagerOnDutyService mods;

	public PermissionController(PermissionService perms, SessionService sessions, ManagerOnDutyService mods) {
		this.perms = perms;
		this.sessions = sessions;
		this.mods = mods;
	}

	/** Everyone on the schedule, checked if the owner lets them be manager on duty. */
	@GetMapping("/api/owner/manager-on-duty")
	public List<ManagerOnDutyService.Candidate> modList(
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return mods.candidates();
	}

	@PutMapping("/api/owner/manager-on-duty")
	public List<ManagerOnDutyService.Candidate> setMod(@RequestBody ManagerOnDutyService.CandidateInput body,
			@CookieValue(name = SessionService.COOKIE_NAME, required = false) String token) {
		sessions.requireOwner(token);
		return mods.set(body);
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

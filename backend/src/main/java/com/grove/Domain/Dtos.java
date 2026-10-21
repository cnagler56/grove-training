package com.grove.Domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Request/response shapes for the video and team APIs. */
public final class Dtos {

	private Dtos() {}

	/** A video as listed in the library, with whether the viewer has watched it. */
	public record VideoSummary(
		Long id,
		String title,
		String description,
		String category,
		VideoStatus status,
		Integer durationSeconds,
		String thumbnailUrl,
		boolean completed,
		LocalDateTime createdAt) {}

	/** One video plus a short-lived signed player URL (null until READY). */
	public record VideoDetail(VideoSummary video, String embedUrl) {}

	/** Admin create/edit body. */
	public record VideoRequest(String title, String description, String category) {}

	/**
	 * What the browser needs to upload a file straight to Bunny over TUS, so the
	 * video bytes never pass through our server.
	 */
	public record UploadTicket(
		Long videoId,
		String endpoint,
		String libraryId,
		String bunnyVideoId,
		long expires,
		String signature) {}

	/** Who has / hasn't watched a video (admin). */
	public record CompletionReport(Long videoId, List<CompletionRow> employees) {}

	public record CompletionRow(Long userId, String name, String email, LocalDateTime completedAt) {}

	/** An employee on the admin Team pages. */
	public record TeamMember(
		Long userId,
		String firstName,
		String lastName,
		String email,
		String phone,
		LocalDate hireDate,
		LocalDate birthday,
		Role role,
		boolean active,
		LocalDateTime disabledAt,
		long videosCompleted,
		LocalDateTime createdAt) {}

	/** Manager edit of an employee's details. Email is also their sign-in. */
	public record EmployeeDetails(
		String firstName,
		String lastName,
		String email,
		String phone,
		LocalDate hireDate,
		LocalDate birthday) {}

	public record TeamMemberUpdate(Boolean active, Role role) {}

	/** An owner adding someone directly (instead of them signing up with the team code). */
	public record NewEmployee(
		String firstName,
		String lastName,
		String email,
		String phone,
		LocalDate hireDate,
		LocalDate birthday,
		Role role,
		String password) {}

	public record PasswordReset(String password) {}

	/** What the public /apply form sends. {@code website} is a honeypot: real people leave it empty. */
	public record ApplicationRequest(
		String firstName,
		String lastName,
		String email,
		String phone,
		String position,
		Boolean licensed,
		String licenseNumber,
		Integer yearsExperience,
		String availability,
		String portfolio,
		String message,
		String website) {}

	/** Owner's changes to an application. */
	public record ApplicationUpdate(ApplicationStatus status, String ownerNotes) {}

	/** One shift as shown on a schedule. */
	public record ShiftView(
		Long id,
		Long userId,
		String name,
		Role role,
		LocalDate date,
		LocalTime start,
		LocalTime end,
		String note) {}

	/**
	 * A Saturday-to-Friday week. Unposted weeks show staff no shifts. The roster
	 * is the Name column: everyone in a scheduled role, receptionists last.
	 */
	public record WeekSchedule(
		LocalDate weekStart,
		LocalDate weekEnd,
		boolean published,
		LocalDateTime publishedAt,
		boolean canEdit,
		List<RosterEntry> roster,
		List<ShiftView> shifts) {}

	/** One row of the schedule. {@code active=false} only for a former employee with shifts that week. */
	public record RosterEntry(Long userId, String name, Role role, boolean active) {}

	/** One cell from the schedule editor. */
	public record ShiftInput(Long userId, LocalDate date, LocalTime start, LocalTime end, String note) {}

	/** The whole week from the editor; replaces whatever was saved before. */
	public record WeekSave(List<ShiftInput> shifts) {}

	public record WeekPublish(boolean published) {}

	/** Whether uploads are wired up — the admin page shows setup help when false. */
	public record AdminStatus(boolean bunnyConfigured, boolean signupOpen) {}
}

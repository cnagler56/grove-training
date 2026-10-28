package com.grove.Domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Request/response shapes for the video and team APIs. */
public final class Dtos {

	private Dtos() {}

	/** A video as listed in the library, with whether the viewer has watched it. */
	public record VideoSummary(
		Long id,
		String title,
		String description,
		String category,
		VideoSource source,
		VideoStatus status,
		Integer durationSeconds,
		String thumbnailUrl,
		boolean completed,
		LocalDateTime createdAt,
		/** Shown only to assigned people (managers always see it). */
		boolean assignedOnly,
		/** The viewer is one of the people it's assigned to. */
		boolean assignedToMe) {}

	/** Who can see a video: everyone on staff, or only {@code userIds}. */
	public record VideoAudience(boolean everyone, List<Long> userIds) {}

	/** One video plus a short-lived signed player URL (null until READY). */
	public record VideoDetail(VideoSummary video, String embedUrl) {}

	/** Admin create/edit body. */
	public record VideoRequest(String title, String description, String category) {}

	/** Admin adds a YouTube video by link instead of uploading a file. */
	public record YouTubeRequest(String url, String title, String description, String category) {}

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
		/** Off the schedule since this date (time off); null = on the schedule. */
		LocalDate inactiveSince,
		/** Name as Salon Ultimate shows it; links their weekly numbers. */
		String salonUltimateName,
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

	/** {@code inactive}: true takes them off the schedule (time off), false puts them back. */
	public record TeamMemberUpdate(Boolean active, Role role, Boolean inactive) {}

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
		/** WORK, or REQUESTED_OFF ("RO", no times). */
		ShiftType type,
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
		/** DRAFT, PENDING (sent for approval), or POSTED. */
		String state,
		/** Viewer can build/save this week (always false for a posted week unless they can approve). */
		boolean canEdit,
		/** Viewer can approve, post, take down, or send back. */
		boolean canApprove,
		String submittedByName,
		Long submittedToId,
		String submittedToName,
		LocalDateTime submittedAt,
		/** Note from an approver who sent the week back, shown to builders. */
		String returnNote,
		String returnedByName,
		/** Who this week can be sent to for approval (for builders; excludes the viewer). */
		List<Person> approvers,
		List<RosterEntry> roster,
		List<ShiftView> shifts) {}

	/** One row of the schedule. {@code active=false} only for a former employee with shifts that week. */
	public record RosterEntry(Long userId, String name, Role role, boolean active) {}

	/** One cell from the schedule editor. {@code type} null means WORK; REQUESTED_OFF ignores the times. */
	public record ShiftInput(Long userId, LocalDate date, ShiftType type, LocalTime start, LocalTime end, String note) {}

	/** The whole week from the editor; replaces whatever was saved before. */
	public record WeekSave(List<ShiftInput> shifts) {}

	public record WeekPublish(boolean published) {}

	/** What the signed-in person can do with the schedule (drives tabs and buttons). */
	public record MyPermissions(boolean viewSchedule, boolean editSchedule, boolean approveSchedule) {}

	/** One permission on the owner's Settings page and the roles that have it. */
	public record PermissionRule(Permission permission, String label, String description, List<Role> roles) {}

	/** Owner's changes: permission name → roles. */
	public record PermissionUpdate(java.util.Map<Permission, List<Role>> roles) {}

	/** Someone the schedule can be sent to for approval. */
	public record Person(Long userId, String name, Role role) {}

	/** A week waiting for the viewer's approval (Home page card). */
	public record PendingWeek(LocalDate weekStart, String submittedByName, LocalDateTime submittedAt) {}

	public record WeekSubmit(Long approverId) {}

	public record WeekSendBack(String note) {}

	/**
	 * A birthday or work anniversary. {@code years} is set for anniversaries only;
	 * ages are never shared.
	 */
	public record Celebration(Long userId, String name, String kind, LocalDate date, Integer years) {}

	/** Today's celebrations (everyone on staff) and the next week's (managers and up). */
	public record Celebrations(LocalDate today, List<Celebration> todays, List<Celebration> upcoming) {}

	/** Whether uploads are wired up — the admin page shows setup help when false. */
	public record AdminStatus(boolean bunnyConfigured, boolean signupOpen) {}
	/** One week of someone's numbers. {@code serviceSalesPerHour} is service sales ÷ floor hours (null with no hours). */
	public record StatLine(
		LocalDate weekStart,
		BigDecimal serviceSales,
		BigDecimal netServiceSales,
		BigDecimal retailSales,
		BigDecimal productPerGuest,
		BigDecimal floorHours,
		BigDecimal offFloorHours,
		BigDecimal serviceSalesPerHour,
		/** Production per hour as payroll figures it: (after-discount sales + 20% of discounts) ÷ floor hours. */
		BigDecimal pph,
		/** That week's hourly rate from the pay matrix and their code. Only ever sent to the employee themselves. */
		BigDecimal payRate,
		/** The rest are also only for the employee themselves (null elsewhere, or for weeks pulled before tips were saved). */
		BigDecimal tips,
		/** Tips ÷ floor hours. */
		BigDecimal tipsPerHour,
		/** Pay rate + tips per hour. */
		BigDecimal totalPerHour,
		/** Services done and clients seen that week (null for weeks pulled before these were saved). */
		Integer services,
		Integer clients) {}

	/** How the last pull of a week went. */
	public record StatsImportView(LocalDate weekStart, String status, String message, Integer rowCount, LocalDateTime attemptedAt) {}

	/** The signed-in person's own weeks, newest first. {@code linked=false}: no Salon Ultimate name set yet. */
	public record MyStats(boolean linked, String salonUltimateName, List<StatLine> weeks) {}

	/** One person on the leaderboard. {@code userId} is null if no site account is linked to this name. */
	public record TeamStatRow(String suName, Long userId, String name, StatLine stats) {}

	/** Everyone's numbers for one week (managers and up). */
	public record TeamStats(
		LocalDate weekStart,
		/** The latest week that has finished (the one pulled automatically on Saturday). */
		LocalDate latestCompleteWeek,
		/** Every week with numbers, newest first. */
		List<LocalDate> weeks,
		boolean configured,
		StatsImportView lastImport,
		List<TeamStatRow> rows) {}

	/** A name from Salon Ultimate and who on the site it's linked to, for the Team page picker. */
	public record SalonUltimateName(String name, Long linkedUserId, String linkedTo) {}

	public record SalonUltimateLink(String name) {}

	/** A matrix step as it appears in the payroll skill's matrix.json. */
	public record PayMatrixEntry(BigDecimal pph, BigDecimal rate) {}

	/** A pay code as it appears in the payroll skill's employee_codes.json. */
	public record PayCodeEntry(Integer code, BigDecimal premium, @JsonProperty("flat_rate") BigDecimal flatRate) {}

	/** The owner loading the skill's two files. {@code codes} is keyed "Last, First", like the file. */
	public record PayRulesUpload(List<PayMatrixEntry> matrix, Map<String, PayCodeEntry> codes) {}

	/** One person's code on the owner's Settings page ("First Last", as Salon Ultimate shows it). */
	public record PayCodeView(String name, Integer code, BigDecimal premium, BigDecimal flatRate) {}

	/** What's loaded, for the owner's Settings page. */
	public record PayRules(List<PayMatrixEntry> matrix, List<PayCodeView> codes) {}
}

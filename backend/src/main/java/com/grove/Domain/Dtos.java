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

	/** Admin adds a YouTube, Instagram, or TikTok video by link instead of uploading a file. */
	public record VideoLinkRequest(String url, String title, String description, String category) {}

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

	public record PasswordChange(String currentPassword, String newPassword) {}

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
		List<ShiftView> shifts,
		/** Every day this week someone is approved off (also merged into shifts as RO where the day is empty). */
		List<TimeOffDay> timeOff,
		/** Private notes by employee id. Only sent to people who can build or approve the schedule. */
		Map<Long, String> builderNotes) {}

	/** One row of the schedule. {@code active=false} only for a former employee with shifts that week. */
	public record RosterEntry(Long userId, String name, Role role, boolean active) {}

	/** One cell from the schedule editor. {@code type} null means WORK; REQUESTED_OFF ignores the times. */
	public record ShiftInput(Long userId, LocalDate date, ShiftType type, LocalTime start, LocalTime end, String note) {}

	/** The whole week from the editor; replaces whatever was saved before. */
	/** {@code notes}: private notes by employee id. Left out, the saved notes stay as they are. */
	public record WeekSave(List<ShiftInput> shifts, Map<Long, String> notes) {}

	public record WeekPublish(boolean published) {}

	/** What the signed-in person can do with the schedule (drives tabs and buttons). */
	public record MyPermissions(boolean viewSchedule, boolean editSchedule, boolean approveSchedule, boolean approveTimeOff, boolean postUpdates,
		boolean payrollNotes, boolean viewPayroll) {}

	/** Starting a Team board post. {@code withPhotos}: photos follow, so the text may be blank. */
	public record TeamPostInput(String body, boolean withPhotos) {}

	public record TeamCommentInput(String body) {}

	/** A device's push subscription, from the browser. */
	public record PushSubscribeInput(String endpoint, String p256dh, String auth) {}

	public record PushUnsubscribeInput(String endpoint) {}

	/** What a push service answered to a test: status 0 means it couldn't be reached. */
	public record PushTestResult(String service, int status, String answer) {}

	public record TeamPinInput(boolean pinned) {}

	public record TeamCommentView(Long id, String authorName, String body, LocalDateTime createdAt, boolean canDelete) {}

	public record TeamPostView(
		Long id,
		String authorName,
		String body,
		boolean pinned,
		LocalDateTime createdAt,
		List<Long> imageIds,
		int likes,
		boolean likedByMe,
		List<TeamCommentView> comments,
		boolean canDelete) {}

	public record TeamFeed(List<TeamPostView> posts, boolean hasMore) {}

	/** What an employee sends to ask for days off. {@code endDate} is the last day off. */
	public record TimeOffInput(LocalDate startDate, LocalDate endDate, String note) {}

	/** An approver's answer. Required when sending a request back. */
	public record TimeOffDecision(String message) {}

	/** A time off request. {@code status} is PENDING, APPROVED, or SENT_BACK. */
	public record TimeOffView(
		Long id,
		Long userId,
		String name,
		LocalDate startDate,
		LocalDate endDate,
		String note,
		String status,
		String responseNote,
		String decidedByName,
		LocalDateTime decidedAt,
		LocalDateTime createdAt) {}

	/** What an approver sees: requests waiting on them, and approved time off still to come. */
	public record TimeOffQueue(List<TimeOffView> waiting, List<TimeOffView> approved) {}

	/** What an employee sends to ask for paid time off. */
	public record VacationPayInput(BigDecimal hours, LocalDate checkDate) {}

	/** A paid time off request. {@code status} is PENDING, APPROVED, or SENT_BACK. */
	public record VacationPayView(
		Long id,
		Long userId,
		String name,
		BigDecimal hours,
		LocalDate checkDate,
		String status,
		String responseNote,
		String decidedByName,
		LocalDateTime decidedAt,
		LocalDateTime createdAt,
		/** Set once the hours are entered into payroll. */
		String finishedByName,
		LocalDateTime finishedAt) {}

	/** What an owner sees: requests waiting on them, and approved ones not yet paid. */
	public record VacationPayQueue(List<VacationPayView> waiting, List<VacationPayView> approved) {}

	/** A day someone is approved off, on the week it falls in. */
	public record TimeOffDay(Long userId, String name, LocalDate date) {}

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
	/** One week of someone's numbers. {@code serviceSalesPerHour} is figured like payroll's PPH: (after-discount sales + 20% of discounts) ÷ floor hours (null with no hours). */
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
		Integer clients,
		/** Color, treatment, and waxing services (null for weeks pulled before these were saved). */
		Integer colors,
		Integer treatments,
		Integer waxes) {}

	/** The whole team's services by type for one week. */
	public record TeamServiceWeek(LocalDate weekStart, Long colors, Long treatments, Long waxes) {}

	/** How the last pull of a week went. */
	public record StatsImportView(LocalDate weekStart, String status, String message, Integer rowCount, LocalDateTime attemptedAt) {}

	/** The signed-in person's own weeks, newest first. {@code linked=false}: no Salon Ultimate name set yet. */
	public record MyStats(boolean linked, String salonUltimateName, List<StatLine> weeks, List<TeamServiceWeek> teamWeeks) {}

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
		List<TeamStatRow> rows,
		List<TeamServiceWeek> teamWeeks) {}

	/** A name from Salon Ultimate and who on the site it's linked to, for the Team page picker. */
	public record SalonUltimateName(String name, Long linkedUserId, String linkedTo) {}

	public record SalonUltimateLink(String name) {}

	/** A matrix step as it appears in the payroll skill's matrix.json. */
	public record PayMatrixEntry(BigDecimal pph, BigDecimal rate) {}


	/** The owner loading the skill's two files. {@code codes} is keyed "Last, First", like the file. */
	public record PayRulesUpload(List<PayMatrixEntry> matrix) {}


	/** What's loaded, for the owner's Settings page. */
	public record PayRules(List<PayMatrixEntry> matrix, BigDecimal minimumRate, BigDecimal leadBump, BigDecimal managerBump,
		List<PayPerson> people) {}

	/**
	 * Someone on the owner's pay table. {@code role} is their site role when linked (LEAD and MANAGER
	 * get a bump). A null {@code flatRate} means they're paid on the matrix.
	 */
	public record PayPerson(String name, Role role, BigDecimal flatRate) {}

	/** The owner setting one person's flat rate; null puts them back on the matrix. */
	public record PayPersonInput(String name, BigDecimal flatRate) {}

	public record MinimumInput(BigDecimal amount) {}

	/** The owner setting the lead or manager bump. */
	public record BumpInput(Role role, BigDecimal amount) {}

	/** One of the two weeks a check pays for, and how its Salon Ultimate pull went (null: never pulled). */
	public record PayrollWeek(LocalDate weekStart, StatsImportView pull) {}

	/** Someone's numbers for one week, as the payroll skill reports them for ADP. */
	public record PayrollPayWeek(
		BigDecimal rate,
		BigDecimal pph,
		BigDecimal floorHours,
		BigDecimal offFloorHours,
		BigDecimal tips,
		BigDecimal retail,
		BigDecimal productPerGuest,
		BigDecimal commission) {}

	/** One person on the payroll report. A week is null when they weren't in that week's report. */
	public record PayrollPayRow(
		String suName,
		/** "Last, First", like the skill. */
		String name,
		/** Their site role when linked; leads and managers get a bump. */
		Role role,
		/** Their flat rate, or null when paid on the matrix. */
		BigDecimal flatRate,
		/** Something to fix before trusting the rate, e.g. not linked to a site account; null when fine. */
		String problem,
		PayrollPayWeek week1,
		PayrollPayWeek week2) {}

	/** Hours and pay for a pay period, built from the two weeks pulled from Salon Ultimate. */
	public record PayrollReport(
		/** False until the owner loads the pay matrix on Settings; rates are blank until then. */
		boolean ratesLoaded,
		/** False when the server has no Salon Ultimate sign-in. */
		boolean salonUltimateConfigured,
		List<PayrollWeek> weeks,
		List<PayrollPayRow> rows,
		BigDecimal floorHours,
		BigDecimal offFloorHours,
		BigDecimal tips,
		BigDecimal commission) {}

	/** A pay period: the check date and the two Saturday-to-Friday weeks it pays for. */
	public record PayPeriod(LocalDate checkDate, LocalDate workStart, LocalDate workEnd) {}

	/** The pay periods to pick from, newest first, and the one to show by default. */
	public record PayPeriods(List<PayPeriod> periods, LocalDate defaultCheckDate) {}

	/** What someone enters on the Payroll notes tab. kind is ADDITION, DEDUCTION, or COMMENT; amount is dollars. */
	public record PayrollNoteInput(Long userId, LocalDate checkDate, String kind, BigDecimal amount, String note) {}

	public record PayrollNoteView(
		Long id,
		Long userId,
		String name,
		LocalDate checkDate,
		String kind,
		BigDecimal amount,
		String note,
		String createdByName,
		LocalDateTime createdAt,
		String updatedByName,
		LocalDateTime updatedAt,
		/** NEW, ACKNOWLEDGED, or FINISHED (entered into payroll). */
		String status,
		String acknowledgedByName,
		LocalDateTime acknowledgedAt,
		String finishedByName,
		LocalDateTime finishedAt) {}

	/** One employee on the Payroll tab: their paid time off and notes for the period. */
	public record PayrollEmployee(
		Long userId,
		String name,
		BigDecimal ptoHours,
		BigDecimal pendingPtoHours,
		/** Each paid time off request on this check, so approved ones can be marked Finished. */
		List<PayrollPto> pto,
		BigDecimal additions,
		BigDecimal deductions,
		List<PayrollNoteView> notes) {}

	/** Everything for one pay period on the Payroll tab. */
	public record PayrollSummary(
		PayPeriod period,
		List<PayrollEmployee> employees,
		BigDecimal ptoHours,
		BigDecimal additions,
		BigDecimal deductions,
		int pendingPtoRequests,
		/** Notes not yet marked Finished. */
		int openNotes,
		/** Approved paid time off not yet marked Finished. */
		int openPto) {}

	/** One paid time off request on the Payroll tab. status is PENDING or APPROVED. */
	public record PayrollPto(Long id, BigDecimal hours, String status, String finishedByName, LocalDateTime finishedAt) {}
}

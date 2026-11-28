package com.grove.Domain;

/**
 * The kinds of notifications the site sends. Each person can turn any of them off
 * for themselves on My account. Who turned a kind off is kept in app_settings under
 * {@link #settingKey()} as a list of user ids ("3,7").
 */
public enum NotificationKind {
	TIME_OFF_REQUESTS("Time off requests",
		"Someone asks for time off, or cancels time off that was approved."),
	PTO_REQUESTS("Paid time off requests",
		"Someone asks for paid time off, or cancels paid time off that was approved."),
	MY_REQUESTS("Answers to your requests",
		"Your time off or paid time off is approved or sent back, or your paid time off is entered into payroll."),
	SCHEDULE("Schedule",
		"A new week is posted, or a week is sent to you to approve or sent back to you."),
	PAYROLL_NOTES("Payroll notes",
		"A payroll note is added or changed, or one you wrote is acknowledged or entered into payroll."),
	VIDEOS("Training videos",
		"A video is assigned to you."),
	CONTESTS("Contests",
		"Your place in a contest changes, a team takes the lead, or a contest ends."),
	TEAM_BOARD("Team board",
		"Someone posts on the team board, or comments on your post."),
	OPEN_TIMECARDS("Open timecards",
		"Someone is still clocked in 30 minutes after closing."),
	DAILY_SALES("Daily sales summary",
		"That day's sales, 30 minutes after closing."),
	JOB_APPLICATIONS("Job applications",
		"Someone applies for a job on the website.");

	private final String label;
	private final String description;

	NotificationKind(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public String label() { return label; }
	public String description() { return description; }

	/**
	 * Where the people who turned this off are kept. The daily sales summary keeps the
	 * key it had before these choices existed, so earlier choices on Settings still count.
	 */
	public String settingKey() {
		return this == DAILY_SALES ? "dailySales.summaryOff" : "notify.off." + name();
	}
}

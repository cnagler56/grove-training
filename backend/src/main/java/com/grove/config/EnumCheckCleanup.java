package com.grove.config;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate gives every enum column a CHECK (col in ('A','B',...)) when it first
 * creates a table, and ddl-auto=update never changes it again. So adding a value
 * to an enum later (say a new contest handicap) makes every insert of that value
 * fail with "Check constraint ... is violated". The Java enums already limit the
 * values, so on MySQL we drop those generated checks at startup.
 */
@Component
public class EnumCheckCleanup implements ApplicationRunner {

	private final DataSource dataSource;
	private final JdbcTemplate jdbc;

	public EnumCheckCleanup(DataSource dataSource, JdbcTemplate jdbc) {
		this.dataSource = dataSource;
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			try (Connection c = dataSource.getConnection()) {
				if (!c.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) return;
			}
			List<Map<String, Object>> checks = jdbc.queryForList("""
				SELECT tc.TABLE_NAME AS t, cc.CONSTRAINT_NAME AS n, cc.CHECK_CLAUSE AS clause
				FROM information_schema.CHECK_CONSTRAINTS cc
				JOIN information_schema.TABLE_CONSTRAINTS tc
				  ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
				WHERE cc.CONSTRAINT_SCHEMA = DATABASE() AND tc.CONSTRAINT_TYPE = 'CHECK'
				""");
			for (Map<String, Object> row : checks) {
				String clause = String.valueOf(row.get("clause"));
				// Only Hibernate's enum lists: (`col` in (_utf8mb4'A', ...)).
				if (!clause.matches("(?s)\\(`\\w+` in \\(.*\\)\\)")) continue;
				String table = String.valueOf(row.get("t"));
				String name = String.valueOf(row.get("n"));
				jdbc.execute("ALTER TABLE `" + table.replace("`", "") + "` DROP CHECK `" + name.replace("`", "") + "`");
				System.out.println("[SCHEMA] dropped enum check " + table + "." + name);
			}
		} catch (Exception e) {
			System.out.println("[SCHEMA] couldn't clean up enum checks: " + e.getMessage());
		}
	}
}

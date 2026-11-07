package com.example.musictournament;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class DatabaseMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("Running automatic database schema column migrations...");

        // Migrations for quiz_playlists table
        String[] playlistColumns = {
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS added_count INTEGER DEFAULT 0",
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS total_found INTEGER DEFAULT 0",
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS duplicate_count INTEGER DEFAULT 0",
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS error_count INTEGER DEFAULT 0",
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS processed_count INTEGER DEFAULT 0",
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS item_count INTEGER DEFAULT 0",
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS sync_status VARCHAR(50) DEFAULT 'IDLE'",
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS last_sync_at TIMESTAMP",
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS last_error_message TEXT",
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE",
                "ALTER TABLE quiz_playlists ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
        };

        for (String sql : playlistColumns) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                // Table might not exist yet before Hibernate initializes or already updated
                log.debug("Skipping playlist migration [{}]: {}", sql, e.getMessage());
            }
        }

        // Migrations for quiz_songs table
        String[] songColumns = {
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS romaji_title VARCHAR(255)",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS aliases TEXT",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS artwork_url VARCHAR(500)",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS preview_url VARCHAR(500)",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS youtube_url VARCHAR(500)",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS spotify_url VARCHAR(500)",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS genre VARCHAR(100) DEFAULT 'General'",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS difficulty INTEGER DEFAULT 50",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS difficulty_manually_set BOOLEAN DEFAULT FALSE",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS popularity_score INTEGER DEFAULT 50",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE",
                "ALTER TABLE quiz_songs ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
        };

        for (String sql : songColumns) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.debug("Skipping song migration [{}]: {}", sql, e.getMessage());
            }
        }

        // Automatic synchronization of verified romaji titles for registered songs
        try {
            var verifiedMap = com.example.musictournament.service.JapaneseRomanizer.getVerifiedTitleDict();
            for (var entry : verifiedMap.entrySet()) {
                jdbcTemplate.update(
                        "UPDATE quiz_songs SET romaji_title = ? WHERE (title = ? OR title LIKE ?) AND (romaji_title IS NULL OR romaji_title <> ?)",
                        entry.getValue(), entry.getKey(), "%" + entry.getKey() + "%", entry.getValue()
                );
            }
        } catch (Exception e) {
            log.debug("Skipping romaji title verification sync: {}", e.getMessage());
        }

        log.info("Database schema column migrations completed successfully.");
    }
}

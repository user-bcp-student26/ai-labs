package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final String INSERT_DEFAULT_USER_SQL = "INSERT OR IGNORE INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)";
    private static final String STUDENT_USERNAME = "student";
    private static final String STUDENT_PASSWORD = "Workshop123!";
    private static final String STUDENT_DISPLAY_NAME = "Workshop Student";
    private static final String STUDENT_ROLE = "STUDENT";
    private static final String TRAINER_USERNAME = "trainer";
    private static final String TRAINER_PASSWORD = "Trainer123!";
    private static final String TRAINER_DISPLAY_NAME = "Workshop Trainer";
    private static final String TRAINER_ROLE = "TRAINER";

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(Path.of("data"));
            try (Connection connection = DriverManager.getConnection(DB_URL);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
                insertDefaultUser(connection, STUDENT_USERNAME, STUDENT_PASSWORD, STUDENT_DISPLAY_NAME, STUDENT_ROLE);
                insertDefaultUser(connection, TRAINER_USERNAME, TRAINER_PASSWORD, TRAINER_DISPLAY_NAME, TRAINER_ROLE);
                migratePlaintextPasswords(connection);
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    public UserRecord findByCredentials(String username, String password) {
        String sql = "SELECT id, username, display_name, role, password FROM legacy_users WHERE username=?";
        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !PasswordHasher.verify(password, result.getString("password"))) {
                    return null;
                }
                return new UserRecord(result.getLong("id"), result.getString("username"), result.getString("display_name"), result.getString("role"));
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
        return null;
    }

    private static void insertDefaultUser(Connection connection, String username, String password,
                                          String displayName, String role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_DEFAULT_USER_SQL)) {
            statement.setString(1, username);
            statement.setString(2, PasswordHasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private static void migratePlaintextPasswords(Connection connection) throws SQLException {
        List<StoredPassword> plaintextPasswords = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT username, password FROM legacy_users");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                String storedPassword = result.getString("password");
                if (!PasswordHasher.isEncoded(storedPassword)) {
                    plaintextPasswords.add(new StoredPassword(result.getString("username"), storedPassword));
                }
            }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE legacy_users SET password=? WHERE username=?")) {
            for (StoredPassword storedPassword : plaintextPasswords) {
                statement.setString(1, PasswordHasher.hash(storedPassword.password()));
                statement.setString(2, storedPassword.username());
                statement.executeUpdate();
            }
        }
    }

    private record StoredPassword(String username, String password) {}
}

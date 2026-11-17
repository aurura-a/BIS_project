package kz.gymbooking.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final RowMapper<UserRecord> ROW_MAPPER = (rs, rowNum) -> new UserRecord(
            rs.getLong("id"),
            rs.getString("full_name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getInt("token_version"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UserRecord> findByEmail(String email) {
        return jdbc.query("SELECT * FROM users WHERE email = ?", ROW_MAPPER, email).stream().findFirst();
    }

    public Optional<UserRecord> findById(long id) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public boolean lockById(long id) {
        return !jdbc.query("SELECT id FROM users WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong(1), id).isEmpty();
    }

    public boolean existsByIdAndRole(long id, String role) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM users WHERE id = ? AND role = ?)",
                Boolean.class, id, role);
        return Boolean.TRUE.equals(exists);
    }

    public UserRecord create(String fullName, String email, String phone, String passwordHash, String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users (full_name, email, phone, password_hash, role)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, fullName, email, phone, passwordHash, role);
        return findById(id).orElseThrow();
    }

    public List<UserRecord> findAll(String role, String query, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (role != null) {
            sql.append(" AND role = ?");
            args.add(role);
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (full_name ILIKE ? OR email ILIKE ?)");
            String pattern = "%" + query + "%";
            args.add(pattern);
            args.add(pattern);
        }
        sql.append(" ORDER BY id LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public boolean updateProfile(long id, String fullName, String phone) {
        return jdbc.update("""
                UPDATE users
                SET full_name = COALESCE(?, full_name), phone = COALESCE(?, phone)
                WHERE id = ?
                """, fullName, phone, id) == 1;
    }

    public boolean updateRole(long id, String role) {
        return jdbc.update("""
                UPDATE users SET role = ?, token_version = token_version + 1
                WHERE id = ?
                """, role, id) == 1;
    }

    public boolean rotateTokenVersion(long id, int expectedVersion) {
        return jdbc.update("""
                UPDATE users SET token_version = token_version + 1
                WHERE id = ? AND token_version = ?
                """, id, expectedVersion) == 1;
    }

    public void revokeTokens(long id) {
        jdbc.update("UPDATE users SET token_version = token_version + 1 WHERE id = ?", id);
    }

    public record UserRecord(long id, String fullName, String email, String phone, String passwordHash,
                             String role, int tokenVersion, OffsetDateTime createdAt) {
    }
}

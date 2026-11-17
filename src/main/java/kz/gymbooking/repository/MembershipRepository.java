package kz.gymbooking.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MembershipRepository {

    private static final RowMapper<Membership> ROW_MAPPER = (rs, rowNum) -> new Membership(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("plan_name"),
            rs.getObject("starts_on", LocalDate.class),
            rs.getObject("ends_on", LocalDate.class),
            (Integer) rs.getObject("visit_limit"),
            (Integer) rs.getObject("visits_remaining"),
            rs.getInt("price_kzt"),
            rs.getString("status"));

    private final JdbcTemplate jdbc;

    public MembershipRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long create(long userId, String planName, LocalDate startsOn, LocalDate endsOn,
                       Integer visitLimit, Integer visitsRemaining, int priceKzt) {
        return jdbc.queryForObject("""
                INSERT INTO memberships
                    (user_id, plan_name, starts_on, ends_on, visit_limit, visits_remaining, price_kzt, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                RETURNING id
                """, Long.class, userId, planName, startsOn, endsOn, visitLimit, visitsRemaining, priceKzt);
    }

    public List<Membership> findByUserId(long userId) {
        return jdbc.query("""
                SELECT * FROM memberships WHERE user_id = ? ORDER BY starts_on DESC
                """, ROW_MAPPER, userId);
    }

    public boolean isActiveOn(long userId, LocalDate date) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM memberships
                    WHERE user_id = ? AND status = 'ACTIVE' AND starts_on <= ? AND ends_on >= ?
                      AND (visits_remaining IS NULL OR visits_remaining > 0)
                )
                """, Boolean.class, userId, date, date);
        return Boolean.TRUE.equals(exists);
    }

    public boolean consumeVisit(long userId, LocalDate date) {
        List<Long> limited = jdbc.query("""
                SELECT id FROM memberships
                WHERE user_id = ? AND status = 'ACTIVE' AND starts_on <= ? AND ends_on >= ?
                  AND visits_remaining IS NOT NULL AND visits_remaining > 0
                ORDER BY starts_on DESC
                LIMIT 1
                FOR UPDATE
                """, (rs, rowNum) -> rs.getLong(1), userId, date, date);
        if (!limited.isEmpty()) {
            return jdbc.update("""
                    UPDATE memberships SET visits_remaining = visits_remaining - 1
                    WHERE id = ? AND visits_remaining > 0
                    """, limited.getFirst()) == 1;
        }
        Boolean unlimited = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM memberships
                    WHERE user_id = ? AND status = 'ACTIVE' AND starts_on <= ? AND ends_on >= ?
                      AND visits_remaining IS NULL
                )
                """, Boolean.class, userId, date, date);
        return Boolean.TRUE.equals(unlimited);
    }

    public boolean hasActiveOverlap(long userId, LocalDate startsOn, LocalDate endsOn) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM memberships
                    WHERE user_id = ? AND status = 'ACTIVE'
                      AND starts_on <= ? AND ends_on >= ?
                )
                """, Boolean.class, userId, endsOn, startsOn);
        return Boolean.TRUE.equals(exists);
    }

    public record Membership(long id, long userId, String planName, LocalDate startsOn, LocalDate endsOn,
                             Integer visitLimit, Integer visitsRemaining, int priceKzt, String status) {
    }
}

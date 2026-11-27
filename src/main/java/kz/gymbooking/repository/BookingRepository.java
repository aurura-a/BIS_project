package kz.gymbooking.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import kz.gymbooking.dto.BookingStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BookingRepository {

    private static final RowMapper<Booking> ROW_MAPPER = (rs, rowNum) -> new Booking(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getLong("class_id"),
            rs.getString("status"),
            rs.getObject("booked_at", OffsetDateTime.class),
            rs.getObject("cancelled_at", OffsetDateTime.class),
            rs.getObject("checked_in_at", OffsetDateTime.class),
            rs.getObject("qr_identifier", UUID.class));

    private final JdbcTemplate jdbc;

    public BookingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Booking> findById(long id) {
        return jdbc.query("SELECT * FROM bookings WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<Booking> findByIdempotencyKey(long userId, UUID key) {
        return jdbc.query("""
                SELECT * FROM bookings WHERE user_id = ? AND idempotency_key = ?
                """, ROW_MAPPER, userId, key).stream().findFirst();
    }

    public boolean hasActiveBooking(long userId, long classId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM bookings WHERE user_id = ? AND class_id = ? AND status = 'BOOKED'
                )
                """, Boolean.class, userId, classId);
        return Boolean.TRUE.equals(exists);
    }

    public long create(long userId, long classId, UUID idempotencyKey) {
        return jdbc.queryForObject("""
                INSERT INTO bookings (user_id, class_id, idempotency_key, status)
                VALUES (?, ?, ?, 'BOOKED')
                RETURNING id
                """, Long.class, userId, classId, idempotencyKey);
    }

    public boolean cancel(long bookingId) {
        return jdbc.update("""
                UPDATE bookings SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'BOOKED'
                """, bookingId) == 1;
    }

    public List<String> listForClass(long classId) {
        return jdbc.query("""
                SELECT id, user_id, status FROM bookings
                WHERE class_id = ? ORDER BY booked_at
                """, (rs, rowNum) -> "#" + rs.getLong("id") + " client #" + rs.getLong("user_id")
                + ", " + rs.getString("status") + "\n", classId);
    }

    public List<String> listForUser(long userId, BookingStatus status, Boolean upcoming, int limit, int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT b.id, b.status, b.booked_at, c.name, c.start_time
                FROM bookings b JOIN classes c ON c.id = b.class_id
                WHERE b.user_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (status != null) {
            sql.append(" AND b.status = ?");
            args.add(status.name());
        }
        if (Boolean.TRUE.equals(upcoming)) {
            sql.append(" AND c.start_time > CURRENT_TIMESTAMP");
        }
        sql.append(" ORDER BY c.start_time LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), (rs, rowNum) -> "#" + rs.getLong("id") + " "
                + rs.getString("name") + ", " + rs.getObject("start_time", OffsetDateTime.class)
                + ", " + rs.getString("status") + "\n", args.toArray());
    }

    public record Booking(long id, long userId, long classId, String status, OffsetDateTime bookedAt,
                          OffsetDateTime cancelledAt, OffsetDateTime checkedInAt, UUID qrIdentifier) {
    }
}

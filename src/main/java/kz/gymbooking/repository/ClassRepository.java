package kz.gymbooking.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClassRepository {

    private static final RowMapper<GymClass> ROW_MAPPER = (rs, rowNum) -> new GymClass(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("type"),
            rs.getLong("trainer_id"),
            rs.getObject("start_time", OffsetDateTime.class),
            rs.getInt("duration_min"),
            rs.getString("room"),
            rs.getInt("capacity"),
            rs.getInt("available_seats"),
            rs.getString("status"));

    private final JdbcTemplate jdbc;

    public ClassRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<GymClass> findAll(OffsetDateTime from, OffsetDateTime to, Integer trainerId, String type,
                                  boolean onlyAvailable, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM classes WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (from != null) {
            sql.append(" AND start_time >= ?");
            args.add(from);
        }
        if (to != null) {
            sql.append(" AND start_time <= ?");
            args.add(to);
        }
        if (trainerId != null) {
            sql.append(" AND trainer_id = ?");
            args.add(trainerId);
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND type = ?");
            args.add(type);
        }
        if (onlyAvailable) {
            sql.append(" AND available_seats > 0");
        }
        sql.append(" ORDER BY start_time LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public Optional<GymClass> findById(long id) {
        return jdbc.query("SELECT * FROM classes WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<GymClass> lockById(long id) {
        return jdbc.query("SELECT * FROM classes WHERE id = ? FOR UPDATE", ROW_MAPPER, id).stream().findFirst();
    }

    public long create(String name, String type, long trainerId, OffsetDateTime startTime, int durationMin,
                       String room, int capacity) {
        return jdbc.queryForObject("""
                INSERT INTO classes
                    (name, type, trainer_id, start_time, duration_min, room, capacity, available_seats, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED')
                RETURNING id
                """, Long.class, name, type, trainerId, startTime, durationMin, room, capacity, capacity);
    }

    public boolean update(GymClass gymClass) {
        return jdbc.update("""
                UPDATE classes
                SET name = ?, type = ?, trainer_id = ?, start_time = ?, duration_min = ?,
                    room = ?, capacity = ?, available_seats = ?, status = ?
                WHERE id = ?
                """, gymClass.name(), gymClass.type(), gymClass.trainerId(), gymClass.startTime(),
                gymClass.durationMin(), gymClass.room(), gymClass.capacity(), gymClass.availableSeats(),
                gymClass.status(), gymClass.id()) == 1;
    }

    public boolean cancel(long id) {
        return jdbc.update("UPDATE classes SET status = 'CANCELLED' WHERE id = ?", id) == 1;
    }

    public boolean decrementAvailableSeats(long id) {
        return jdbc.update("""
                UPDATE classes SET available_seats = available_seats - 1
                WHERE id = ? AND available_seats > 0
                """, id) == 1;
    }

    public boolean incrementAvailableSeats(long id) {
        return jdbc.update("""
                UPDATE classes SET available_seats = available_seats + 1
                WHERE id = ? AND available_seats < capacity
                """, id) == 1;
    }

    public record GymClass(long id, String name, String type, long trainerId, OffsetDateTime startTime,
                           int durationMin, String room, int capacity, int availableSeats, String status) {
    }
}

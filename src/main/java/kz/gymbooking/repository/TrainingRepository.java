package kz.gymbooking.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import kz.gymbooking.dto.ExerciseMuscleGroupResponse;
import kz.gymbooking.dto.ExerciseResponse;
import kz.gymbooking.dto.MuscleGroupResponse;
import kz.gymbooking.dto.TrainingChartPoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TrainingRepository {

    private final JdbcTemplate jdbc;

    public TrainingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MuscleGroupResponse> findMuscleGroups() {
        return jdbc.query("""
                SELECT id, name, description
                FROM muscle_groups
                ORDER BY name
                """, (rs, rowNum) -> new MuscleGroupResponse(
                rs.getLong("id"), rs.getString("name"), rs.getString("description")));
    }

    public List<ExerciseResponse> findExercises(Long muscleGroupId) {
        StringBuilder sql = new StringBuilder("""
                SELECT exercises.id, exercises.name, exercises.description,
                       muscle_groups.id AS muscle_group_id, muscle_groups.name AS muscle_group,
                       exercise_muscle_groups.role AS muscle_group_role
                FROM exercises
                JOIN exercise_muscle_groups ON exercise_muscle_groups.exercise_id = exercises.id
                JOIN muscle_groups ON muscle_groups.id = exercise_muscle_groups.muscle_group_id
                """);
        List<Object> args = new ArrayList<>();
        if (muscleGroupId != null) {
            sql.append("""
                     WHERE EXISTS (
                         SELECT 1
                         FROM exercise_muscle_groups matching_group
                         WHERE matching_group.exercise_id = exercises.id
                           AND matching_group.muscle_group_id = ?
                     )
                    """);
            args.add(muscleGroupId);
        }
        sql.append(" ORDER BY exercises.name, muscle_groups.name");

        List<ExerciseRow> rows = jdbc.query(sql.toString(), (rs, rowNum) -> new ExerciseRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getLong("muscle_group_id"),
                rs.getString("muscle_group"),
                rs.getString("muscle_group_role")), args.toArray());
        Map<Long, ExerciseBuilder> exercises = new LinkedHashMap<>();
        for (ExerciseRow row : rows) {
            ExerciseBuilder exercise = exercises.computeIfAbsent(row.id(), ignored ->
                    new ExerciseBuilder(row.id(), row.name(), row.description()));
            exercise.muscleGroups.add(new ExerciseMuscleGroupResponse(
                    row.muscleGroupId(), row.muscleGroup(), row.role()));
        }

        return exercises.values().stream()
                .map(exercise -> new ExerciseResponse(
                        exercise.id, exercise.name, exercise.description, List.copyOf(exercise.muscleGroups)))
                .toList();
    }

    public List<TrainingChartPoint> findDemoChart(OffsetDateTime from, OffsetDateTime to, String interval) {
        return jdbc.query("""
                SELECT date_trunc(?, workouts.started_at AT TIME ZONE 'UTC')::date AS period_start,
                       muscle_groups.id AS muscle_group_id,
                       muscle_groups.name AS muscle_group,
                       COUNT(DISTINCT workouts.id) AS workout_count,
                       SUM(workout_exercises.sets_count) AS total_sets,
                       SUM(workout_exercises.sets_count * workout_exercises.reps_per_set) AS total_reps,
                       SUM(workout_exercises.sets_count * workout_exercises.reps_per_set
                           * COALESCE(workout_exercises.weight_kg, 0)) AS total_volume_kg
                FROM workout_sessions workouts
                JOIN users ON users.id = workouts.user_id
                JOIN workout_exercises ON workout_exercises.workout_id = workouts.id
                JOIN exercise_muscle_groups
                    ON exercise_muscle_groups.exercise_id = workout_exercises.exercise_id
                JOIN muscle_groups ON muscle_groups.id = exercise_muscle_groups.muscle_group_id
                WHERE users.email = 'demo-chart@gym.local'
                  AND workouts.started_at >= ?
                  AND workouts.started_at < ?
                GROUP BY 1, muscle_groups.id, muscle_groups.name
                ORDER BY period_start, muscle_groups.name
                """, (rs, rowNum) -> new TrainingChartPoint(
                rs.getObject("period_start", java.time.LocalDate.class),
                rs.getLong("muscle_group_id"),
                rs.getString("muscle_group"),
                rs.getLong("workout_count"),
                rs.getLong("total_sets"),
                rs.getLong("total_reps"),
                rs.getBigDecimal("total_volume_kg")),
                interval, from, to);
    }

    private record ExerciseRow(
            long id, String name, String description, long muscleGroupId, String muscleGroup, String role) {
    }

    private static final class ExerciseBuilder {
        private final long id;
        private final String name;
        private final String description;
        private final List<ExerciseMuscleGroupResponse> muscleGroups = new ArrayList<>();

        private ExerciseBuilder(long id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }
}

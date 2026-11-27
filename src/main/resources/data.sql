INSERT INTO users (full_name, email, password_hash, role)
VALUES ('Demo Chart User', 'demo-chart@gym.local', NULL, 'CLIENT')
ON CONFLICT (email) DO NOTHING;

INSERT INTO muscle_groups (name, description)
VALUES
    ('Chest', 'Pectoral muscles'),
    ('Back', 'Upper and lower back muscles'),
    ('Shoulders', 'Deltoid muscles'),
    ('Arms', 'Biceps and triceps'),
    ('Legs', 'Quadriceps, hamstrings, and glutes')
ON CONFLICT (name) DO NOTHING;

INSERT INTO exercises (name, description)
VALUES
    ('Bench Press', 'Barbell chest press'),
    ('Push-Up', 'Bodyweight chest press'),
    ('Lat Pulldown', 'Cable pull for the back'),
    ('Squat', 'Barbell squat'),
    ('Shoulder Press', 'Overhead press'),
    ('Biceps Curl', 'Dumbbell curl')
ON CONFLICT (name) DO NOTHING;

INSERT INTO exercise_muscle_groups (exercise_id, muscle_group_id, role)
SELECT exercises.id, muscle_groups.id, mappings.role
FROM (VALUES
    ('Bench Press', 'Chest', 'PRIMARY'),
    ('Bench Press', 'Arms', 'SECONDARY'),
    ('Push-Up', 'Chest', 'PRIMARY'),
    ('Push-Up', 'Shoulders', 'SECONDARY'),
    ('Lat Pulldown', 'Back', 'PRIMARY'),
    ('Lat Pulldown', 'Arms', 'SECONDARY'),
    ('Squat', 'Legs', 'PRIMARY'),
    ('Shoulder Press', 'Shoulders', 'PRIMARY'),
    ('Shoulder Press', 'Arms', 'SECONDARY'),
    ('Biceps Curl', 'Arms', 'PRIMARY')
) AS mappings(exercise_name, muscle_group_name, role)
JOIN exercises ON exercises.name = mappings.exercise_name
JOIN muscle_groups ON muscle_groups.name = mappings.muscle_group_name
ON CONFLICT (exercise_id, muscle_group_id) DO UPDATE
SET role = EXCLUDED.role;

DELETE FROM workout_sessions
WHERE user_id = (SELECT id FROM users WHERE email = 'demo-chart@gym.local')
  AND notes = 'Demo chart data';

INSERT INTO workout_sessions (user_id, started_at, duration_min, notes)
SELECT users.id, samples.started_at, samples.duration_min, 'Demo chart data'
FROM users
CROSS JOIN (VALUES
    ((CURRENT_DATE - 28 + TIME '08:00') AT TIME ZONE 'UTC', 55),
    ((CURRENT_DATE - 21 + TIME '18:00') AT TIME ZONE 'UTC', 60),
    ((CURRENT_DATE - 14 + TIME '08:00') AT TIME ZONE 'UTC', 50),
    ((CURRENT_DATE - 7 + TIME '18:00') AT TIME ZONE 'UTC', 65),
    ((CURRENT_DATE - 3 + TIME '08:00') AT TIME ZONE 'UTC', 45),
    ((CURRENT_DATE - 1 + TIME '18:00') AT TIME ZONE 'UTC', 55),
    ((CURRENT_DATE + TIME '08:00') AT TIME ZONE 'UTC', 60)
) AS samples(started_at, duration_min)
WHERE users.email = 'demo-chart@gym.local'
ON CONFLICT (user_id, started_at) DO NOTHING;

INSERT INTO workout_exercises (workout_id, exercise_id, sets_count, reps_per_set, weight_kg)
SELECT workouts.id, exercises.id, samples.sets_count, samples.reps_per_set, samples.weight_kg
FROM (VALUES
    (CURRENT_DATE - 28 + TIME '08:00', 'Bench Press', 4, 8, 50.00),
    (CURRENT_DATE - 28 + TIME '08:00', 'Lat Pulldown', 3, 10, 40.00),
    (CURRENT_DATE - 28 + TIME '08:00', 'Squat', 4, 8, 60.00),
    (CURRENT_DATE - 21 + TIME '18:00', 'Push-Up', 3, 12, 0.00),
    (CURRENT_DATE - 21 + TIME '18:00', 'Shoulder Press', 3, 10, 20.00),
    (CURRENT_DATE - 21 + TIME '18:00', 'Biceps Curl', 3, 12, 10.00),
    (CURRENT_DATE - 14 + TIME '08:00', 'Squat', 4, 10, 65.00),
    (CURRENT_DATE - 14 + TIME '08:00', 'Lat Pulldown', 4, 10, 42.50),
    (CURRENT_DATE - 14 + TIME '08:00', 'Bench Press', 4, 8, 52.50),
    (CURRENT_DATE - 7 + TIME '18:00', 'Shoulder Press', 4, 8, 22.50),
    (CURRENT_DATE - 7 + TIME '18:00', 'Push-Up', 4, 12, 0.00),
    (CURRENT_DATE - 7 + TIME '18:00', 'Biceps Curl', 3, 12, 12.50),
    (CURRENT_DATE - 3 + TIME '08:00', 'Bench Press', 4, 8, 55.00),
    (CURRENT_DATE - 3 + TIME '08:00', 'Lat Pulldown', 3, 10, 45.00),
    (CURRENT_DATE - 3 + TIME '08:00', 'Squat', 4, 8, 70.00),
    (CURRENT_DATE - 1 + TIME '18:00', 'Shoulder Press', 4, 10, 22.50),
    (CURRENT_DATE - 1 + TIME '18:00', 'Biceps Curl', 4, 12, 12.50),
    (CURRENT_DATE + TIME '08:00', 'Push-Up', 4, 15, 0.00),
    (CURRENT_DATE + TIME '08:00', 'Squat', 4, 10, 70.00),
    (CURRENT_DATE + TIME '08:00', 'Bench Press', 4, 8, 55.00)
) AS samples(workout_time, exercise_name, sets_count, reps_per_set, weight_kg)
JOIN users ON users.email = 'demo-chart@gym.local'
JOIN workout_sessions workouts
    ON workouts.user_id = users.id
    AND workouts.started_at = samples.workout_time AT TIME ZONE 'UTC'
JOIN exercises ON exercises.name = samples.exercise_name
ON CONFLICT (workout_id, exercise_id) DO NOTHING;

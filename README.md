# Gym Booking Backend

This is my gym booking backend. I use Java 21, Spring Boot, Maven, and PostgreSQL.
The frontend is not part of my current work.

## Project structure

- `controller` contains the API endpoints.
- `dto` contains request and response types.
- `repository` contains database queries.
- `src/main/resources/schema.sql` creates the database tables.
- `src/main/resources/data.sql` adds the demo exercise and workout data.
- `src/main/resources/static/gym-booking-api.yaml` describes the API.

## Database

Create a PostgreSQL database:

```sql
CREATE DATABASE gym_db;
```

Set the connection details and start the application:

```sh
export DB_URL=jdbc:postgresql://localhost:5432/gym_db
export DB_USERNAME="$(whoami)"
export DB_PASSWORD=
mvn spring-boot:run
```

Spring Boot runs `schema.sql` and `data.sql` when the application starts.
The SQL uses conflict checks so the exercise catalog is not duplicated. The
workout rows marked `Demo chart data` are rebuilt at each start with dates
relative to the current day.

The database has these training tables:

- `muscle_groups` stores muscle group names.
- `exercises` stores exercise names and descriptions.
- `exercise_muscle_groups` links exercises to muscle groups. An exercise can
  use more than one group. Each link is marked as primary or secondary.
- `workout_sessions` stores the user and start time for a workout.
- `workout_exercises` stores the exercises, sets, reps, and weight in a workout.

Exercise responses list the linked muscle groups and mark each one as primary or
secondary.

The demo data belongs to `demo-chart@gym.local`. This is a sample account for
the chart endpoint and does not have a password. Do not use it as a real user
account.

## Training API

The API base address is `http://localhost:8080/api/v1`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/muscle-groups` | List muscle groups |
| GET | `/exercises` | List exercises |
| GET | `/exercises?muscleGroupId=1` | List exercises for a muscle group |
| GET | `/demo/training-chart` | Get daily chart data for the last 28 days |
| GET | `/demo/training-chart?from=2026-11-01&to=2026-11-27&interval=WEEK` | Get weekly chart data for a date range |

Chart results include the period, muscle group, workout count, sets, reps, and
training volume in kilograms. An exercise can be linked to both primary and
secondary muscle groups, so its values can appear under more than one group.
Do not add the group totals together to calculate the total for a workout.

The fitness device endpoints and `/users/me/metrics` are still stubs. They are
separate from the exercise and workout data used by the training chart.

## Other API areas

The backend also has endpoints for authentication, users, trainers, classes,
bookings, memberships, and gym access. The full API contract is in
`src/main/resources/static/gym-booking-api.yaml`.

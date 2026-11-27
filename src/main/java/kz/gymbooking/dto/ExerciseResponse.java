package kz.gymbooking.dto;

import java.util.List;

public record ExerciseResponse(
        long id, String name, String description, List<ExerciseMuscleGroupResponse> muscleGroups) {
}

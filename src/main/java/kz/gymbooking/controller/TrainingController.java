package kz.gymbooking.controller;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.validation.constraints.Positive;
import kz.gymbooking.dto.ChartInterval;
import kz.gymbooking.dto.ExerciseResponse;
import kz.gymbooking.dto.MuscleGroupResponse;
import kz.gymbooking.dto.TrainingChartPoint;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.TrainingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "application/json")
public class TrainingController {

    private final TrainingRepository training;

    public TrainingController(TrainingRepository training) {
        this.training = training;
    }

    @GetMapping("/muscle-groups")
    public List<MuscleGroupResponse> muscleGroups() {
        return training.findMuscleGroups();
    }

    @GetMapping("/exercises")
    public List<ExerciseResponse> exercises(
            @RequestParam(required = false) @Positive Long muscleGroupId) {
        return training.findExercises(muscleGroupId);
    }

    @GetMapping("/demo/training-chart")
    public List<TrainingChartPoint> demoTrainingChart(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "DAY") ChartInterval interval) {
        LocalDate end = to == null ? LocalDate.now(ZoneOffset.UTC) : to;
        LocalDate start = from == null ? end.minusDays(27) : from;
        if (start.isAfter(end)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "VALIDATION_FAILED", "'from' must be on or before 'to'");
        }

        return training.findDemoChart(start.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
                end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime(),
                interval.name().toLowerCase());
    }
}

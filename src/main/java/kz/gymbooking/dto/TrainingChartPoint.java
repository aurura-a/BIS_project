package kz.gymbooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrainingChartPoint(
        LocalDate periodStart,
        long muscleGroupId,
        String muscleGroup,
        long workoutCount,
        long totalSets,
        long totalReps,
        BigDecimal totalVolumeKg) {
}

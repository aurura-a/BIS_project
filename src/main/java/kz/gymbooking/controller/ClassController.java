package kz.gymbooking.controller;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.CancelClassRequest;
import kz.gymbooking.dto.GymClassCreate;
import kz.gymbooking.dto.GymClassUpdate;
import kz.gymbooking.service.ClassService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/classes", produces = "text/plain;charset=UTF-8")
public class ClassController {

    private final ClassService classService;

    public ClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) OffsetDateTime from,
                       @RequestParam(required = false) OffsetDateTime to,
                       @RequestParam(required = false) Integer trainerId,
                       @RequestParam(required = false) String type,
                       @RequestParam(defaultValue = "false") boolean onlyAvailable,
                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                       @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return classService.list(from, to, trainerId, type, onlyAvailable, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String create(@Valid @RequestBody GymClassCreate request) {
        return classService.create(request);
    }

    @GetMapping("/{classId}")
    public String get(@PathVariable int classId) {
        return classService.get(classId);
    }

    @PatchMapping("/{classId}")
    public String update(@PathVariable int classId, @Valid @RequestBody GymClassUpdate request) {
        return classService.update(classId, request);
    }

    @PostMapping("/{classId}/cancel")
    public String cancel(@PathVariable int classId, @Valid @RequestBody(required = false) CancelClassRequest req) {
        return classService.cancel(classId);
    }

    @GetMapping("/{classId}/bookings")
    public String bookings(@PathVariable int classId) {
        return classService.listBookings(classId);
    }
}

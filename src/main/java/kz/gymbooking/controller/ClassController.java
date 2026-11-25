package kz.gymbooking.controller;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.CancelClassRequest;
import kz.gymbooking.dto.GymClassCreate;
import kz.gymbooking.dto.GymClassUpdate;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.ClassRepository;
import kz.gymbooking.repository.UserRepository;
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

    private final ClassRepository classes;
    private final BookingRepository bookings;
    private final UserRepository users;

    public ClassController(ClassRepository classes, BookingRepository bookings, UserRepository users) {
        this.classes = classes;
        this.bookings = bookings;
        this.users = users;
    }

    @GetMapping
    public String list(@RequestParam(required = false) OffsetDateTime from,
                       @RequestParam(required = false) OffsetDateTime to,
                       @RequestParam(required = false) Integer trainerId,
                       @RequestParam(required = false) String type,
                       @RequestParam(defaultValue = "false") boolean onlyAvailable,
                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                       @RequestParam(defaultValue = "0") @Min(0) int offset) {
        var found = classes.findAll(from, to, trainerId, type, onlyAvailable, limit, offset);
        StringBuilder body = new StringBuilder("total: ").append(found.size()).append('\n');
        found.forEach(gymClass -> body.append('#').append(gymClass.id()).append(' ').append(gymClass.name())
                .append(", ").append(gymClass.startTime()).append(", ").append(gymClass.room())
                .append(", trainer #").append(gymClass.trainerId()).append(", ")
                .append(gymClass.capacity() - gymClass.availableSeats()).append('/').append(gymClass.capacity())
                .append(", ").append(gymClass.status()).append('\n'));
        return body.toString();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String create(@Valid @RequestBody GymClassCreate req) {
        requireTrainer(req.trainerId());
        long id = classes.create(req.name(), req.type(), req.trainerId(), req.startTime(),
                req.durationMin(), req.room(), req.capacity());
        return "id: " + id + "\nname: " + req.name() + "\nstartTime: " + req.startTime()
                + "\nroom: " + req.room() + "\ncapacity: " + req.capacity() + "\nstatus: SCHEDULED\n";
    }

    @GetMapping("/{classId}")
    public String get(@PathVariable int classId) {
        var gymClass = classes.findById(classId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Class not found"));
        return "id: " + gymClass.id() + "\nname: " + gymClass.name() + "\ntype: " + gymClass.type()
                + "\ntrainer: #" + gymClass.trainerId() + "\nstartTime: " + gymClass.startTime()
                + "\ndurationMin: " + gymClass.durationMin() + "\nroom: " + gymClass.room()
                + "\ncapacity: " + gymClass.capacity() + "\nbookedCount: "
                + (gymClass.capacity() - gymClass.availableSeats()) + "\nstatus: " + gymClass.status() + "\n";
    }

    @PatchMapping("/{classId}")
    public String update(@PathVariable int classId, @Valid @RequestBody GymClassUpdate req) {
        var gymClass = classes.findById(classId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Class not found"));
        long trainerId = req.trainerId() == null ? gymClass.trainerId() : req.trainerId();
        requireTrainer(trainerId);
        var updated = new ClassRepository.GymClass(gymClass.id(),
                req.name() == null ? gymClass.name() : req.name(),
                gymClass.type(),
                trainerId,
                req.startTime() == null ? gymClass.startTime() : req.startTime(),
                req.durationMin() == null ? gymClass.durationMin() : req.durationMin(),
                req.room() == null ? gymClass.room() : req.room(),
                gymClass.capacity(),
                gymClass.availableSeats(),
                gymClass.status());
        if (!classes.update(updated)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Class not found");
        }
        return "id: " + classId + "\nupdated\n";
    }

    private void requireTrainer(long trainerId) {
        if (!users.existsByIdAndRole(trainerId, "TRAINER")) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_STATE",
                    "User does not have the TRAINER role");
        }
    }

    @PostMapping("/{classId}/cancel")
    public String cancel(@PathVariable int classId, @Valid @RequestBody(required = false) CancelClassRequest req) {
        if (!classes.cancel(classId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Class not found");
        }
        return "id: " + classId + "\nstatus: CANCELLED\n";
    }

    @GetMapping("/{classId}/bookings")
    public String bookings(@PathVariable int classId) {
        if (classes.findById(classId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Class not found");
        }
        return String.join("", bookings.listForClass(classId));
    }
}

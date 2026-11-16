package kz.gymbooking.service;

import java.time.OffsetDateTime;
import java.util.List;

import kz.gymbooking.dto.GymClassCreate;
import kz.gymbooking.dto.GymClassUpdate;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.ClassRepository;
import kz.gymbooking.repository.ClassRepository.GymClass;
import kz.gymbooking.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassService {

    private final ClassRepository classes;
    private final BookingRepository bookings;
    private final UserRepository users;

    public ClassService(ClassRepository classes, BookingRepository bookings, UserRepository users) {
        this.classes = classes;
        this.bookings = bookings;
        this.users = users;
    }

    public String list(OffsetDateTime from, OffsetDateTime to, Integer trainerId, String type,
                       boolean onlyAvailable, int limit, int offset) {
        List<GymClass> result = classes.findAll(from, to, trainerId, type, onlyAvailable, limit, offset);
        StringBuilder body = new StringBuilder("total: ").append(result.size()).append('\n');
        result.forEach(gymClass -> body.append(formatLine(gymClass)));
        return body.toString();
    }

    public String get(long id) {
        return format(classes.findById(id).orElseThrow(this::notFound));
    }

    public String create(GymClassCreate request) {
        requireTrainer(request.trainerId());
        long id = classes.create(request.name(), request.type(), request.trainerId(), request.startTime(),
                request.durationMin(), request.room(), request.capacity());
        return "id: " + id + "\nname: " + request.name() + "\nstartTime: " + request.startTime()
                + "\nroom: " + request.room() + "\ncapacity: " + request.capacity() + "\nstatus: SCHEDULED\n";
    }

    @Transactional
    public String update(long id, GymClassUpdate request) {
        GymClass current = classes.lockById(id).orElseThrow(this::notFound);
        String name = request.name() == null ? current.name() : request.name();
        long trainerId = request.trainerId() == null ? current.trainerId() : request.trainerId();
        requireTrainer(trainerId);
        OffsetDateTime start = request.startTime() == null ? current.startTime() : request.startTime();
        int duration = request.durationMin() == null ? current.durationMin() : request.durationMin();
        String room = request.room() == null ? current.room() : request.room();
        int capacity = request.capacity() == null ? current.capacity() : request.capacity();
        int booked = current.capacity() - current.availableSeats();
        if (capacity < booked) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPACITY_TOO_SMALL", "Capacity is below the booked count");
        }
        GymClass updated = new GymClass(id, name, current.type(), trainerId, start, duration, room, capacity,
                capacity - booked, current.status());
        classes.update(updated);
        return "id: " + id + "\nupdated\n";
    }

    public String cancel(long id) {
        if (!classes.cancel(id)) {
            throw notFound();
        }
        return "id: " + id + "\nstatus: CANCELLED\n";
    }

    public String listBookings(long classId) {
        if (classes.findById(classId).isEmpty()) {
            throw notFound();
        }
        return bookings.listForClass(classId);
    }

    private String format(GymClass gymClass) {
        return "id: " + gymClass.id() + "\nname: " + gymClass.name() + "\ntype: " + gymClass.type()
                + "\ntrainerId: " + gymClass.trainerId() + "\nstartTime: " + gymClass.startTime()
                + "\ndurationMin: " + gymClass.durationMin() + "\nroom: " + gymClass.room()
                + "\ncapacity: " + gymClass.capacity() + "\navailableSeats: " + gymClass.availableSeats()
                + "\nstatus: " + gymClass.status() + "\n";
    }

    private String formatLine(GymClass gymClass) {
        return "#" + gymClass.id() + " " + gymClass.name() + ", " + gymClass.startTime()
                + ", " + gymClass.room() + ", trainer #" + gymClass.trainerId() + ", "
                + (gymClass.capacity() - gymClass.availableSeats()) + "/" + gymClass.capacity()
                + ", " + gymClass.status() + "\n";
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Class not found");
    }

    private void requireTrainer(long userId) {
        if (!users.existsByIdAndRole(userId, "TRAINER")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TRAINER",
                    "Class trainer must be an existing trainer");
        }
    }
}

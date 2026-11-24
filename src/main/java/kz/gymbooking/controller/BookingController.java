package kz.gymbooking.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.BookingStatus;
import kz.gymbooking.dto.CreateBookingRequest;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.ClassRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "text/plain;charset=UTF-8")
public class BookingController {

    private static final long CURRENT_USER_ID = 17;
    private final BookingRepository bookings;
    private final ClassRepository classes;

    public BookingController(BookingRepository bookings, ClassRepository classes) {
        this.bookings = bookings;
        this.classes = classes;
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public String create(@Valid @RequestBody CreateBookingRequest req,
                         @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey) {
        var gymClass = classes.findById(req.classId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Class not found"));
        if ("CANCELLED".equals(gymClass.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASS_NOT_BOOKABLE", "Class is cancelled");
        }
        if (gymClass.availableSeats() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASS_FULL", "Class is full");
        }
        var existing = idempotencyKey == null ? java.util.Optional.<BookingRepository.Booking>empty()
                : bookings.findByIdempotencyKey(CURRENT_USER_ID, idempotencyKey);
        if (existing.isPresent()) {
            var booking = existing.orElseThrow();
            return "id: " + booking.id() + "\nclientId: " + CURRENT_USER_ID + "\nclassId: " + req.classId()
                    + "\nstatus: " + booking.status() + "\n";
        }
        if (bookings.hasActiveBooking(CURRENT_USER_ID, req.classId())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_BOOKED", "You already booked this class");
        }
        long id = bookings.create(CURRENT_USER_ID, req.classId(), idempotencyKey);
        if (!classes.decrementAvailableSeats(req.classId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASS_FULL", "Class is full");
        }
        return "id: " + id + "\nclientId: " + CURRENT_USER_ID + "\nclassId: " + req.classId() + "\nstatus: BOOKED\n";
    }

    @GetMapping("/users/me/bookings")
    public String myBookings(@RequestParam(required = false) BookingStatus status,
                             @RequestParam(required = false) Boolean upcoming,
                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                             @RequestParam(defaultValue = "0") @Min(0) int offset) {
        var found = bookings.listForUser(CURRENT_USER_ID, status, upcoming, limit, offset);
        return "total: " + found.size() + "\n" + String.join("", found);
    }

    @GetMapping("/bookings/{bookingId}")
    public String get(@PathVariable int bookingId) {
        var booking = bookings.findById(bookingId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Booking not found"));
        var gymClass = classes.findById(booking.classId()).orElseThrow();
        return "id: " + booking.id() + "\nclientId: " + booking.userId() + "\nclass: #"
                + gymClass.id() + " " + gymClass.name() + ", " + gymClass.startTime()
                + "\nstatus: " + booking.status() + "\nbookedAt: " + booking.bookedAt() + "\n";
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public String cancel(@PathVariable int bookingId) {
        var booking = bookings.findById(bookingId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Booking not found"));
        if (!bookings.cancel(bookingId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CANCELLED", "Booking is not active");
        }
        if (!classes.incrementAvailableSeats(booking.classId())) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Could not restore class capacity");
        }
        return "id: " + bookingId + "\nstatus: CANCELLED\n";
    }
}

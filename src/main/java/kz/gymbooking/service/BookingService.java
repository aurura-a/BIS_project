package kz.gymbooking.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import kz.gymbooking.dto.BookingStatus;
import kz.gymbooking.dto.CreateBookingRequest;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.BookingRepository.Booking;
import kz.gymbooking.repository.ClassRepository;
import kz.gymbooking.repository.ClassRepository.GymClass;
import kz.gymbooking.repository.MembershipRepository;
import kz.gymbooking.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final ClassRepository classes;
    private final MembershipRepository memberships;
    private final UserRepository users;

    public BookingService(BookingRepository bookings, ClassRepository classes, MembershipRepository memberships,
                          UserRepository users) {
        this.bookings = bookings;
        this.classes = classes;
        this.memberships = memberships;
        this.users = users;
    }

    @Transactional
    public String create(long userId, CreateBookingRequest request, UUID idempotencyKey) {
        if (idempotencyKey != null) {
            if (!users.lockById(userId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
            }
            Booking previous = bookings.findByIdempotencyKey(userId, idempotencyKey).orElse(null);
            if (previous != null) {
                return format(previous);
            }
        }

        GymClass gymClass = classes.lockById(request.classId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Class not found"));
        if (idempotencyKey != null) {
            Booking previous = bookings.findByIdempotencyKey(userId, idempotencyKey).orElse(null);
            if (previous != null) {
                return format(previous);
            }
        }
        if (!"SCHEDULED".equals(gymClass.status()) || !gymClass.startTime().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASS_NOT_BOOKABLE", "Class is not bookable");
        }
        if (!memberships.isActiveOn(userId, gymClass.startTime().toLocalDate())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_MEMBERSHIP",
                    "An active membership is required");
        }
        if (bookings.hasActiveBooking(userId, gymClass.id())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_BOOKED", "You already booked this class");
        }
        if (gymClass.availableSeats() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASS_FULL", "Class is full");
        }

        long bookingId = bookings.create(userId, gymClass.id(), idempotencyKey);
        if (!memberships.consumeVisit(userId, gymClass.startTime().toLocalDate())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_MEMBERSHIP",
                    "Membership has no visits remaining");
        }
        if (!classes.decrementAvailableSeats(gymClass.id())) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASS_FULL", "Class is full");
        }
        return bookings.findById(bookingId).map(this::format).orElseThrow();
    }

    public String listForUser(long userId, BookingStatus status, Boolean upcoming, int limit, int offset) {
        List<String> records = bookings.listForUser(userId, status, upcoming, limit, offset);
        return "total: " + records.size() + "\n" + String.join("", records);
    }

    public String get(long userId, long bookingId) {
        Booking booking = bookings.findById(bookingId)
                .filter(row -> row.userId() == userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Booking not found"));
        return format(booking);
    }

    @Transactional
    public String cancel(long userId, long bookingId) {
        Booking initial = bookings.findById(bookingId)
                .filter(row -> row.userId() == userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Booking not found"));
        GymClass gymClass = classes.lockById(initial.classId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Class not found"));
        Booking booking = bookings.findById(bookingId).orElseThrow();
        if (!"BOOKED".equals(booking.status())) {
            return format(booking);
        }
        if (OffsetDateTime.now().isAfter(gymClass.startTime().minusHours(2))) {
            throw new ApiException(HttpStatus.CONFLICT, "CANCEL_DEADLINE_PASSED",
                    "Booking can no longer be cancelled");
        }
        if (!bookings.cancel(bookingId) || !classes.incrementAvailableSeats(gymClass.id())) {
            throw new IllegalStateException("Could not cancel booking and release its seat");
        }
        return bookings.findById(bookingId).map(this::format).orElseThrow();
    }

    private String format(Booking booking) {
        return "id: " + booking.id() + "\nclientId: " + booking.userId() + "\nclassId: "
                + booking.classId() + "\nstatus: " + booking.status() + "\nbookedAt: " + booking.bookedAt() + "\n";
    }
}

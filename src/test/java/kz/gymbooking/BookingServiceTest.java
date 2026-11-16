package kz.gymbooking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import kz.gymbooking.dto.CreateBookingRequest;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.BookingRepository;
import kz.gymbooking.repository.BookingRepository.Booking;
import kz.gymbooking.repository.ClassRepository;
import kz.gymbooking.repository.ClassRepository.GymClass;
import kz.gymbooking.repository.MembershipRepository;
import kz.gymbooking.repository.UserRepository;
import kz.gymbooking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookings;

    @Mock
    private ClassRepository classes;

    @Mock
    private MembershipRepository memberships;

    @Mock
    private UserRepository users;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(bookings, classes, memberships, users);
    }

    @Test
    void createsBookingConsumesVisitThenReleasesSeat() {
        OffsetDateTime startsAt = OffsetDateTime.now().plusDays(2);
        when(classes.lockById(17)).thenReturn(Optional.of(scheduledClass(17, startsAt, 2)));
        when(memberships.isActiveOn(5, startsAt.toLocalDate())).thenReturn(true);
        when(bookings.hasActiveBooking(5, 17)).thenReturn(false);
        when(bookings.create(5, 17, null)).thenReturn(41L);
        when(memberships.consumeVisit(5, startsAt.toLocalDate())).thenReturn(true);
        when(classes.decrementAvailableSeats(17)).thenReturn(true);
        Booking booking = new Booking(41, 5, 17, "BOOKED", OffsetDateTime.now(), null);
        when(bookings.findById(41)).thenReturn(Optional.of(booking));

        String response = bookingService.create(5, new CreateBookingRequest(17), null);

        assertEquals("id: 41\nclientId: 5\nclassId: 17\nstatus: BOOKED\nbookedAt: "
                + booking.bookedAt() + "\n", response);
        InOrder order = inOrder(bookings, memberships, classes);
        order.verify(bookings).create(5, 17, null);
        order.verify(memberships).consumeVisit(5, startsAt.toLocalDate());
        order.verify(classes).decrementAvailableSeats(17);
    }

    @Test
    void doesNotSaveBookingWhenClassIsFull() {
        OffsetDateTime startsAt = OffsetDateTime.now().plusDays(2);
        when(classes.lockById(17)).thenReturn(Optional.of(scheduledClass(17, startsAt, 0)));
        when(memberships.isActiveOn(5, startsAt.toLocalDate())).thenReturn(true);
        when(bookings.hasActiveBooking(5, 17)).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> bookingService.create(5, new CreateBookingRequest(17), null));

        assertEquals("CLASS_FULL", error.getCode());
        verify(bookings, never()).create(5, 17, null);
        verify(memberships, never()).consumeVisit(5, startsAt.toLocalDate());
    }

    @Test
    void idempotentRetryReturnsPreviousBookingBeforeCheckingClass() {
        UUID key = UUID.randomUUID();
        Booking previous = new Booking(41, 5, 17, "BOOKED", OffsetDateTime.now(), null);
        when(users.lockById(5)).thenReturn(true);
        when(bookings.findByIdempotencyKey(5, key)).thenReturn(Optional.of(previous));

        String response = bookingService.create(5, new CreateBookingRequest(99), key);

        assertEquals("id: 41\nclientId: 5\nclassId: 17\nstatus: BOOKED\nbookedAt: "
                + previous.bookedAt() + "\n", response);
        InOrder order = inOrder(users, bookings);
        order.verify(users).lockById(5);
        order.verify(bookings).findByIdempotencyKey(5, key);
        verify(classes, never()).lockById(99);
    }

    private GymClass scheduledClass(long id, OffsetDateTime startsAt, int availableSeats) {
        return new GymClass(id, "Yoga", "YOGA", 9, startsAt, 60, "Studio 1", 10,
                availableSeats, "SCHEDULED");
    }
}

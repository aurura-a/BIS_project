package kz.gymbooking.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.BookingStatus;
import kz.gymbooking.dto.CreateBookingRequest;
import kz.gymbooking.service.BookingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public String create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBookingRequest request,
                         @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey) {
        return bookingService.create(Long.parseLong(jwt.getSubject()), request, idempotencyKey);
    }

    @GetMapping("/users/me/bookings")
    public String myBookings(@AuthenticationPrincipal Jwt jwt,
                             @RequestParam(required = false) BookingStatus status,
                             @RequestParam(required = false) Boolean upcoming,
                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                             @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return bookingService.listForUser(Long.parseLong(jwt.getSubject()), status, upcoming, limit, offset);
    }

    @GetMapping("/bookings/{bookingId}")
    public String get(@AuthenticationPrincipal Jwt jwt, @PathVariable int bookingId) {
        return bookingService.get(Long.parseLong(jwt.getSubject()), bookingId);
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public String cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable int bookingId) {
        return bookingService.cancel(Long.parseLong(jwt.getSubject()), bookingId);
    }
}

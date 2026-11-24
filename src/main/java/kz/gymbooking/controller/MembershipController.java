package kz.gymbooking.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;
import kz.gymbooking.dto.BuyMembershipRequest;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.MembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "text/plain;charset=UTF-8")
public class MembershipController {

    private static final long CURRENT_USER_ID = 17;
    private final MembershipRepository memberships;

    public MembershipController(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    @GetMapping("/membership-plans")
    public String plans() {
        return """
                #1 Monthly Unlimited, 30 days, unlimited, 25000 KZT
                #2 12 Visits, 60 days, 12 visits, 18000 KZT
                #3 Annual Unlimited, 365 days, unlimited, 220000 KZT
                """;
    }

    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    public String buy(@Valid @RequestBody BuyMembershipRequest req) {
        LocalDate start = req.startDate() == null ? LocalDate.now() : req.startDate();
        String planName;
        Integer visitLimit;
        Integer visitsRemaining;
        int price;
        int duration;
        switch (req.planId()) {
            case 1 -> { planName = "Monthly Unlimited"; duration = 30; visitLimit = null; price = 25000; }
            case 2 -> { planName = "12 Visits"; duration = 60; visitLimit = 12; price = 18000; }
            case 3 -> { planName = "Annual Unlimited"; duration = 365; visitLimit = null; price = 220000; }
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Membership plan not found");
        }
        visitsRemaining = visitLimit;
        LocalDate end = start.plusDays(duration);
        if (memberships.hasActiveOverlap(CURRENT_USER_ID, start, end)) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBERSHIP_OVERLAP", "You already have an active membership");
        }
        long id = memberships.create(CURRENT_USER_ID, planName, start, end, visitLimit, visitsRemaining, price);
        return "id: " + id + "\nplanId: " + req.planId() + "\nstartDate: " + start + "\nstatus: ACTIVE\n";
    }

    @GetMapping("/users/me/memberships")
    public String myMemberships() {
        var found = memberships.findByUserId(CURRENT_USER_ID);
        StringBuilder body = new StringBuilder();
        found.forEach(membership -> body.append('#').append(membership.id()).append(' ').append(membership.planName())
                .append(", ").append(membership.startsOn()).append(" - ").append(membership.endsOn())
                .append(", ").append(membership.status()).append('\n'));
        return body.toString();
    }
}

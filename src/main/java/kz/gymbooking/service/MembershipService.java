package kz.gymbooking.service;

import java.time.LocalDate;
import java.util.Map;

import kz.gymbooking.dto.BuyMembershipRequest;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.MembershipRepository;
import kz.gymbooking.repository.MembershipRepository.Membership;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MembershipService {

    private static final Map<Integer, Plan> PLANS = Map.of(
            1, new Plan("Monthly Unlimited", 30, null, 25_000),
            2, new Plan("12 Visits", 60, 12, 18_000),
            3, new Plan("Annual Unlimited", 365, null, 220_000));

    private final MembershipRepository memberships;

    public MembershipService(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    public String listPlans() {
        return """
                #1 Monthly Unlimited, 30 days, unlimited, 25000 KZT
                #2 12 Visits, 60 days, 12 visits, 18000 KZT
                #3 Annual Unlimited, 365 days, unlimited, 220000 KZT
                """;
    }

    public String buy(long userId, BuyMembershipRequest request) {
        Plan plan = PLANS.get(request.planId());
        if (plan == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PLAN", "Membership plan not found");
        }
        LocalDate startsOn = request.startDate() == null ? LocalDate.now() : request.startDate();
        LocalDate endsOn = startsOn.plusDays(plan.durationDays());
        if (memberships.hasActiveOverlap(userId, startsOn, endsOn)) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBERSHIP_OVERLAP",
                    "You already have an active membership");
        }
        long id = memberships.create(userId, plan.name(), startsOn, endsOn,
                plan.visitLimit(), plan.visitLimit(), plan.priceKzt());
        return "id: " + id + "\nplanId: " + request.planId() + "\nstartDate: " + startsOn
                + "\nstatus: ACTIVE\n";
    }

    public String listForUser(long userId) {
        StringBuilder result = new StringBuilder();
        for (Membership membership : memberships.findByUserId(userId)) {
            result.append('#').append(membership.id()).append(' ').append(membership.planName()).append(", ")
                    .append(membership.startsOn()).append(" - ").append(membership.endsOn()).append(", ")
                    .append(membership.status()).append('\n');
        }
        return result.toString();
    }

    private record Plan(String name, int durationDays, Integer visitLimit, int priceKzt) {
    }
}

package kz.gymbooking.controller;

import jakarta.validation.Valid;
import kz.gymbooking.dto.BuyMembershipRequest;
import kz.gymbooking.service.MembershipService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "text/plain;charset=UTF-8")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/membership-plans")
    public String plans() {
        return membershipService.listPlans();
    }

    @PostMapping("/memberships")
    @ResponseStatus(HttpStatus.CREATED)
    public String buy(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BuyMembershipRequest request) {
        return membershipService.buy(Long.parseLong(jwt.getSubject()), request);
    }

    @GetMapping("/users/me/memberships")
    public String myMemberships(@AuthenticationPrincipal Jwt jwt) {
        return membershipService.listForUser(Long.parseLong(jwt.getSubject()));
    }
}

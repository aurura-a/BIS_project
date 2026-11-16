package kz.gymbooking.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.Role;
import kz.gymbooking.dto.RoleChangeRequest;
import kz.gymbooking.dto.UserUpdate;
import kz.gymbooking.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/users", produces = "text/plain;charset=UTF-8")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public String me(@AuthenticationPrincipal Jwt jwt) {
        return userService.getCurrentUser(Long.parseLong(jwt.getSubject()));
    }

    @PatchMapping("/me")
    public String updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UserUpdate request) {
        return userService.updateCurrentUser(Long.parseLong(jwt.getSubject()), request);
    }

    @GetMapping
    public String list(@RequestParam(required = false) Role role,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                       @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return userService.listUsers(role, q, limit, offset);
    }

    @PatchMapping("/{userId}/role")
    public String changeRole(@PathVariable int userId, @Valid @RequestBody RoleChangeRequest req) {
        return userService.changeRole(userId, req.role());
    }
}

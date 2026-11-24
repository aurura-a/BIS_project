package kz.gymbooking.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import kz.gymbooking.dto.Role;
import kz.gymbooking.dto.RoleChangeRequest;
import kz.gymbooking.dto.UserUpdate;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.UserRepository;
import org.springframework.http.HttpStatus;
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

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/me")
    public String me() {
        var user = users.findById(17).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
        return "id: " + user.id() + "\nemail: " + user.email() + "\nfullName: " + user.fullName()
                + "\nphone: " + (user.phone() == null ? "" : user.phone()) + "\nrole: " + user.role()
                + "\ncreatedAt: " + user.createdAt() + "\n";
    }

    @PatchMapping("/me")
    public String updateMe(@Valid @RequestBody UserUpdate req) {
        if (!users.updateProfile(17, req.fullName(), req.phone())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        }
        var user = users.findById(17).orElseThrow();
        return "id: " + user.id() + "\nfullName: " + user.fullName() + "\nphone: "
                + (user.phone() == null ? "" : user.phone()) + "\nrole: " + user.role() + "\n";
    }

    @GetMapping
    public String list(@RequestParam(required = false) Role role,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
                       @RequestParam(defaultValue = "0") @Min(0) int offset) {
        var found = users.findAll(role == null ? null : role.name(), q, limit, offset);
        StringBuilder body = new StringBuilder("total: ").append(found.size()).append('\n');
        found.forEach(user -> body.append('#').append(user.id()).append(' ').append(user.fullName())
                .append(", ").append(user.email()).append(", ").append(user.role()).append('\n'));
        return body.toString();
    }

    @PatchMapping("/{userId}/role")
    public String changeRole(@PathVariable int userId, @Valid @RequestBody RoleChangeRequest req) {
        if (!users.updateRole(userId, req.role().name())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
        }
        return "id: " + userId + "\nrole: " + req.role() + "\n";
    }
}

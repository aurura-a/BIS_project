package kz.gymbooking.service;

import java.util.List;

import kz.gymbooking.dto.Role;
import kz.gymbooking.dto.UserUpdate;
import kz.gymbooking.error.ApiException;
import kz.gymbooking.repository.UserRepository;
import kz.gymbooking.repository.UserRepository.UserRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public String getCurrentUser(long userId) {
        return format(users.findById(userId).orElseThrow(this::notFound));
    }

    public String listUsers(Role role, String query, int limit, int offset) {
        List<UserRecord> records = users.findAll(role == null ? null : role.name(), query, limit, offset);
        StringBuilder result = new StringBuilder("total: ").append(records.size()).append('\n');
        records.forEach(user -> result.append('#').append(user.id()).append(' ')
                .append(user.fullName()).append(", ").append(user.email()).append(", ").append(user.role()).append('\n'));
        return result.toString();
    }

    public String updateCurrentUser(long userId, UserUpdate request) {
        if (!users.updateProfile(userId, request.fullName(), request.phone())) {
            throw notFound();
        }
        return getCurrentUser(userId);
    }

    public String changeRole(long userId, Role role) {
        if (!users.updateRole(userId, role.name())) {
            throw notFound();
        }
        return "id: " + userId + "\nrole: " + role + "\n";
    }

    private String format(UserRecord user) {
        return "id: " + user.id() + "\nemail: " + user.email() + "\nfullName: " + user.fullName()
                + "\nphone: " + user.phone() + "\nrole: " + user.role() + "\ncreatedAt: "
                + user.createdAt() + "\n";
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found");
    }
}

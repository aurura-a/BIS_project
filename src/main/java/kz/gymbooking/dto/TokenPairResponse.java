package kz.gymbooking.dto;

public record TokenPairResponse(String accessToken, String refreshToken, int expiresIn, UserSummary user) {

    public record UserSummary(long id, String email, String fullName, String phone, String role) {
    }
}

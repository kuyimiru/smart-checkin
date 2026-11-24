package com.checkin.service;
import com.checkin.dto.request.LoginRequest;
import com.checkin.dto.response.LoginResponse;
import com.checkin.entity.User;

public interface UserService {
    LoginResponse login(LoginRequest request);
    void updateProfile(Long userId, User user);
    User getProfile(Long userId);
}

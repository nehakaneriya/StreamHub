package com.neha.StreamHub.services;

import com.neha.StreamHub.dtos.common.UserDto;

public interface AuthService {

    UserDto registerUser(UserDto userDto);

    void cancelRegistration(String email, String otp);
}

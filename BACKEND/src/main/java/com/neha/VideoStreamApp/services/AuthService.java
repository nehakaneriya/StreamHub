package com.neha.VideoStreamApp.services;

import com.neha.VideoStreamApp.dtos.common.UserDto;

public interface AuthService {

    UserDto registerUser(UserDto userDto);

    void cancelRegistration(String email, String otp);
}

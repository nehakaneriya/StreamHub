package com.neha.StreamHub.services;

import com.neha.StreamHub.dtos.common.UserDto;

public interface UserService {


    //create user
    UserDto createUser(UserDto userDto);

    // Registration ke liye — user enable=false se banta hai, OTP verify ke baad enable hoga
    UserDto createPendingUser(UserDto userDto);

    //get all users
    Iterable<UserDto> getAllUsers();

    //get user by email
    UserDto getUserByEmail(String email);

    //get user by id
    UserDto getUserById(String userId);

    //update user
    UserDto updateUser(UserDto userDto, String userId);

    //delete user
    void deleteUser(String userId);


}



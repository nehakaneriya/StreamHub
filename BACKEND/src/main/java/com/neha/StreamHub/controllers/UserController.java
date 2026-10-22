package com.neha.StreamHub.controllers;


import com.neha.StreamHub.config.AppConstants;
import com.neha.StreamHub.dtos.common.UserDto;
import com.neha.StreamHub.services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {


    private final UserService userService;

    //create user api
    // Sirf ADMIN — koi bhi logged-in user apna account enable karwa sakta tha
    // (registration bypass). Normal user registration sirf /api/v1/auth/register se.
    @PreAuthorize("hasRole('"+ AppConstants.ADMIN_ROLE +"')")
    @PostMapping
    public ResponseEntity<UserDto> createUser(@RequestBody UserDto userDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(userDto));
    }

    //get all user api
    // Sirf ADMIN — pehle ye endpoint kisi bhi authenticated user ko poori user table de raha tha
    @PreAuthorize("hasRole('"+ AppConstants.ADMIN_ROLE +"')")
    @GetMapping
    public ResponseEntity<Iterable<UserDto>> getAllUsers(){
        return ResponseEntity.ok(userService.getAllUsers());
    }

    //get user By Email
    @GetMapping("/email/{email}")
    public ResponseEntity<UserDto> getUserByEmail(@PathVariable("email") String email){
        return ResponseEntity.ok(userService.getUserByEmail(email));
    }

    //delete user
    //api/v1/users/{userId}
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable("userId") String userId, Authentication authentication){

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        //Check: ya apna account hai ya ADMIN hai
        UserDto existingUser = userService.getUserById(userId);
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_" + AppConstants.ADMIN_ROLE));
        boolean isOwner = existingUser.getEmail().equals(authentication.getName());

        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    //update user
    @PutMapping("/{userId}")
    public ResponseEntity<UserDto> updateUser(@RequestBody UserDto userDto, @PathVariable("userId") String userId, Authentication authentication){

        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Check: ya apna account hai ya ADMIN hai
        UserDto existingUser = userService.getUserById(userId);
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_" + AppConstants.ADMIN_ROLE));
        boolean isOwner = existingUser.getEmail().equals(authentication.getName());

        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(userService.updateUser(userDto, userId));
    }

    //get User By ID
    @PreAuthorize("hasRole('"+ AppConstants.ADMIN_ROLE +"')")
    @GetMapping("/{userId}")
    public ResponseEntity<UserDto> getUserByID(@PathVariable("userId") String userId){
        return ResponseEntity.ok(userService.getUserById(userId));
    }
}

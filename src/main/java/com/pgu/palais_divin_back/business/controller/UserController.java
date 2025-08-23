package com.pgu.palais_divin_back.business.controller;

import com.pgu.palais_divin_back.business.dto.UserWithRatedRestaurantsDto;
import com.pgu.palais_divin_back.business.model.User;
import com.pgu.palais_divin_back.business.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    public User createUser(@RequestBody User user) {
        return userService.createUser(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return userService.findUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrouver un utilisateur à partir de son mail avec ses restaurants notés
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<UserWithRatedRestaurantsDto> getUserByEmailWithRatings(@PathVariable String email) {
        return userService.findUserByEmailWithRatedRestaurants(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrouver tous les utilisateurs avec leurs infos de base
     */
    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAllUsers();
    }
}

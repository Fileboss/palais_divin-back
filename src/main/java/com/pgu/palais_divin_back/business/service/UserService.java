package com.pgu.palais_divin_back.business.service;

import com.pgu.palais_divin_back.business.dto.UserWithRatedRestaurantsDto;
import com.pgu.palais_divin_back.business.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserService {
    User createUser(User user);

    @Transactional(readOnly = true)
    Optional<User> findUserById(String id);

    @Transactional(readOnly = true)
    Optional<UserWithRatedRestaurantsDto> findUserByEmailWithRatedRestaurants(String email);

    @Transactional(readOnly = true)
    List<User> findAllUsers();
}

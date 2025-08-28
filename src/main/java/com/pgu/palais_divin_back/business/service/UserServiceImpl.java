package com.pgu.palais_divin_back.business.service;

import com.pgu.palais_divin_back.business.dto.RestaurantSummaryDto;
import com.pgu.palais_divin_back.business.dto.UserWithRatedRestaurantsDto;
import com.pgu.palais_divin_back.business.model.User;
import com.pgu.palais_divin_back.business.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    public User createUser(User user) {
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<User> findUserById(String id) {
        return userRepository.findById(UUID.fromString(id));
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<UserWithRatedRestaurantsDto> findUserByEmailWithRatedRestaurants(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();
        List<RestaurantSummaryDto> ratedRestaurants = userRepository.findRatedRestaurantsByEmail(email);

        UserWithRatedRestaurantsDto dto = new UserWithRatedRestaurantsDto();
        dto.setUuid(user.getUuid().toString());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setRatedRestaurants(ratedRestaurants);

        return Optional.of(dto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

}

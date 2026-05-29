package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;

public interface SignupUseCase {

  User signup(String token, String email, String displayName, String password);
}

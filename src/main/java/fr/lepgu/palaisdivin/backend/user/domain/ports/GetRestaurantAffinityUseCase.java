package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.user.domain.model.RestaurantAffinity;

public interface GetRestaurantAffinityUseCase {

  RestaurantAffinity getFor(String requesterSubject, RestaurantId restaurantId);
}

package fr.lepgu.palaisdivin.backend.user.application;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.RestaurantAffinity;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.GetRestaurantAffinityUseCase;
import fr.lepgu.palaisdivin.backend.user.domain.ports.RecommendationGraphPort;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RestaurantAffinityService implements GetRestaurantAffinityUseCase {

  private final UserRepositoryPort users;
  private final RestaurantRepositoryPort restaurants;
  private final RecommendationGraphPort graph;

  public RestaurantAffinityService(
      UserRepositoryPort users,
      RestaurantRepositoryPort restaurants,
      RecommendationGraphPort graph) {
    this.users = users;
    this.restaurants = restaurants;
    this.graph = graph;
  }

  @Override
  public RestaurantAffinity getFor(String requesterSubject, RestaurantId restaurantId) {
    UserId requester = users.requireBySubject(requesterSubject);
    restaurants
        .findById(restaurantId)
        .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    return graph.findAffinityFor(requester, restaurantId);
  }
}

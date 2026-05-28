package fr.lepgu.palaisdivin.backend.restaurant.adapters.geocoding;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/search")
public interface BanApiClient {

  @GetExchange
  BanResponse search(@RequestParam("q") String query, @RequestParam("limit") int limit);
}

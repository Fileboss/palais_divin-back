package fr.lepgu.palaisdivin.backend.restaurant.adapters.postgres;

import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantCursor;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantFilter;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantSort;
import jakarta.persistence.Query;

enum RestaurantSortStrategy {
  CREATED_AT_DESC {
    @Override
    String orderByClause() {
      return "order by r.created_at desc, r.id desc";
    }

    @Override
    String keysetPredicate(RestaurantCursor cursor) {
      return "and (r.created_at < :ck or (r.created_at = :ck and r.id < :cid)) ";
    }

    @Override
    void bindCursorParameters(Query q, RestaurantCursor cursor) {
      RestaurantCursor.ByCreatedAt c = (RestaurantCursor.ByCreatedAt) cursor;
      q.setParameter("ck", c.createdAt());
      q.setParameter("cid", c.id());
    }
  },
  RATING_DESC {
    @Override
    String orderByClause() {
      return "order by r.avg_rating desc nulls last, r.id desc";
    }

    @Override
    String keysetPredicate(RestaurantCursor cursor) {
      RestaurantCursor.ByRating c = (RestaurantCursor.ByRating) cursor;
      return c.avgRating() == null
          ? "and r.avg_rating is null and r.id < :cid "
          : "and (r.avg_rating < :ck or (r.avg_rating = :ck and r.id < :cid) or r.avg_rating is"
              + " null) ";
    }

    @Override
    void bindCursorParameters(Query q, RestaurantCursor cursor) {
      RestaurantCursor.ByRating c = (RestaurantCursor.ByRating) cursor;
      if (c.avgRating() != null) {
        q.setParameter("ck", c.avgRating());
      }
      q.setParameter("cid", c.id());
    }
  },
  NAME_ASC {
    @Override
    String orderByClause() {
      return "order by r.name asc, r.id asc";
    }

    @Override
    String keysetPredicate(RestaurantCursor cursor) {
      return "and (r.name > :ck or (r.name = :ck and r.id > :cid)) ";
    }

    @Override
    void bindCursorParameters(Query q, RestaurantCursor cursor) {
      RestaurantCursor.ByName c = (RestaurantCursor.ByName) cursor;
      q.setParameter("ck", c.name());
      q.setParameter("cid", c.id());
    }
  },
  DISTANCE_ASC {
    @Override
    String orderByClause() {
      return "order by " + RestaurantPostgresAdapter.DIST_EXPR + " asc, r.id asc";
    }

    @Override
    String keysetPredicate(RestaurantCursor cursor) {
      return "and ("
          + RestaurantPostgresAdapter.DIST_EXPR
          + " > :ck or ("
          + RestaurantPostgresAdapter.DIST_EXPR
          + " = :ck and r.id > :cid)) ";
    }

    @Override
    void bindCursorParameters(Query q, RestaurantCursor cursor) {
      RestaurantCursor.ByDistance c = (RestaurantCursor.ByDistance) cursor;
      q.setParameter("ck", c.distanceMetres());
      q.setParameter("cid", c.id());
    }

    @Override
    void bindAnchorParameters(Query q, RestaurantFilter filter) {
      Coordinates anchor = filter.anchor();
      q.setParameter("lng", anchor.longitude());
      q.setParameter("lat", anchor.latitude());
    }
  },
  AFFINITY_DESC {
    @Override
    String orderByClause() {
      throw new IllegalStateException(UNREACHABLE);
    }

    @Override
    String keysetPredicate(RestaurantCursor cursor) {
      throw new IllegalStateException(UNREACHABLE);
    }

    @Override
    void bindCursorParameters(Query q, RestaurantCursor cursor) {
      throw new IllegalStateException(UNREACHABLE);
    }
  };

  private static final String UNREACHABLE =
      "AFFINITY_DESC paginates via the recommendation graph, not Postgres keyset";

  abstract String orderByClause();

  abstract String keysetPredicate(RestaurantCursor cursor);

  abstract void bindCursorParameters(Query q, RestaurantCursor cursor);

  void bindAnchorParameters(Query q, RestaurantFilter filter) {}

  static RestaurantSortStrategy forSort(RestaurantSort sort) {
    return switch (sort) {
      case CREATED_AT_DESC -> CREATED_AT_DESC;
      case RATING_DESC -> RATING_DESC;
      case NAME_ASC -> NAME_ASC;
      case DISTANCE_ASC -> DISTANCE_ASC;
      case AFFINITY_DESC -> AFFINITY_DESC;
    };
  }
}

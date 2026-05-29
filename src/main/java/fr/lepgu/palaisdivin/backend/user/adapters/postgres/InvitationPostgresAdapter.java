package fr.lepgu.palaisdivin.backend.user.adapters.postgres;

import fr.lepgu.palaisdivin.backend.user.domain.model.Invitation;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationId;
import fr.lepgu.palaisdivin.backend.user.domain.model.InvitationToken;
import fr.lepgu.palaisdivin.backend.user.domain.ports.InvitationRepositoryPort;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InvitationPostgresAdapter implements InvitationRepositoryPort {

  private final InvitationJpaRepository jpa;

  InvitationPostgresAdapter(InvitationJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Invitation save(Invitation invitation) {
    return toDomain(jpa.save(toEntity(invitation)));
  }

  @Override
  public Optional<Invitation> findById(InvitationId id) {
    return jpa.findById(id.value()).map(InvitationPostgresAdapter::toDomain);
  }

  @Override
  public Optional<Invitation> findByToken(InvitationToken token) {
    return jpa.findByToken(token.value()).map(InvitationPostgresAdapter::toDomain);
  }

  private static InvitationEntity toEntity(Invitation i) {
    return new InvitationEntity(
        i.id().value(), i.token().value(), i.expiresAt(), i.consumedAt(), i.createdAt());
  }

  private static Invitation toDomain(InvitationEntity e) {
    return new Invitation(
        new InvitationId(e.getId()),
        new InvitationToken(e.getToken()),
        e.getExpiresAt(),
        e.getConsumedAt(),
        e.getCreatedAt());
  }
}

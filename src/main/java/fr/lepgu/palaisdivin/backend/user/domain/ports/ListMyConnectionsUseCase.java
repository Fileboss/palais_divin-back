package fr.lepgu.palaisdivin.backend.user.domain.ports;

import fr.lepgu.palaisdivin.backend.shared.domain.valueobject.CursorPage;
import fr.lepgu.palaisdivin.backend.user.domain.model.Connection;
import fr.lepgu.palaisdivin.backend.user.domain.model.ConnectionCursor;

public interface ListMyConnectionsUseCase {

  CursorPage<Connection> listMine(String subject, ConnectionCursor cursor, int size);
}

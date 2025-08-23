package com.pgu.palais_divin_back.business.model;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.util.UUID;

@Node
@Data
public class User {
    @Id
    private UUID uuid;

    private String firstName;
    private String lastName;
    private String email;

    public User() {
        this.uuid = UUID.randomUUID();
    }
}

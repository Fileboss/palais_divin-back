package fr.lepgu.palaisdivin.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, SharedTestStubs.class})
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {}

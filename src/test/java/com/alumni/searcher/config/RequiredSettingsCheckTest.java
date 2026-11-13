package com.alumni.searcher.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredSettingsCheckTest {

    @Test
    void namesEveryMissingEnvironmentVariable() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "${DB_URL}")
                .withProperty("spring.datasource.username", "${DB_USERNAME}")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");

        assertThatThrownBy(() -> RequiredSettingsCheck.verify(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL")
                .hasMessageContaining("DB_USERNAME")
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void namesOnlyTheVariableThatIsMissing() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_USERNAME", "render_user")
                .withProperty("DB_PASSWORD", "secret")
                .withProperty("spring.datasource.url", "${DB_URL}")
                .withProperty("spring.datasource.username", "${DB_USERNAME}")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");

        assertThatThrownBy(() -> RequiredSettingsCheck.verify(environment))
                .hasMessageContaining("DB_URL")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("DB_USERNAME", "DB_PASSWORD"));
    }

    @Test
    void passesWhenAllSettingsAreProvided() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://db.internal:5432/alumni")
                .withProperty("DB_USERNAME", "render_user")
                .withProperty("DB_PASSWORD", "secret")
                .withProperty("spring.datasource.url", "${DB_URL}")
                .withProperty("spring.datasource.username", "${DB_USERNAME}")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");

        assertThatCode(() -> RequiredSettingsCheck.verify(environment)).doesNotThrowAnyException();
    }

    @Test
    void passesWhenLocalProfileSuppliesDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "${DB_URL:jdbc:postgresql://localhost:5432/alumni_db}")
                .withProperty("spring.datasource.username", "${DB_USERNAME:alumni_user}")
                .withProperty("spring.datasource.password", "${DB_PASSWORD:alumni_password}");

        assertThatCode(() -> RequiredSettingsCheck.verify(environment)).doesNotThrowAnyException();
    }
}

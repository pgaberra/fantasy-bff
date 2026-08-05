package com.fantasy.bff.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void acceptsAPasswordWithUppercaseLowercaseAndDigit() {
        assertThat(validator.isValid("Password1", null)).isTrue();
    }

    @Test
    void acceptsSymbolsAlongsideTheRequiredCharacterClasses() {
        assertThat(validator.isValid("P@ssw0rd!", null)).isTrue();
    }

    @Test
    void rejectsAPasswordWithoutAnUppercaseLetter() {
        assertThat(validator.isValid("password1", null)).isFalse();
    }

    @Test
    void rejectsAPasswordWithoutALowercaseLetter() {
        assertThat(validator.isValid("PASSWORD1", null)).isFalse();
    }

    @Test
    void rejectsAPasswordWithoutADigit() {
        assertThat(validator.isValid("PasswordOnly", null)).isFalse();
    }

    @Test
    void treatsNullAndEmptyAsValidBecauseNotBlankAndSizeReportThoseInstead() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
    }
}

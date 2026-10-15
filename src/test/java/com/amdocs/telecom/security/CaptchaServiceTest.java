package com.amdocs.telecom.security;

import com.amdocs.telecom.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class CaptchaServiceTest {
    private final MutableClock clock = new MutableClock();
    private final CaptchaService captchas = new CaptchaService(clock);
    private final MockHttpSession session = new MockHttpSession();

    @Test void acceptsCorrectAnswerOnlyOnce() {
        CaptchaService.IssuedChallenge form = captchas.issue(session);
        String answer = solve(form.getQuestion());
        assertThat(captchas.consume(session, form.getId(), answer)).isTrue();
        assertThat(captchas.consume(session, form.getId(), answer)).isFalse();
    }
    @Test void wrongAnswerConsumesChallenge() {
        CaptchaService.IssuedChallenge form = captchas.issue(session);
        String answer = solve(form.getQuestion());
        assertThat(captchas.consume(session, form.getId(), "999")).isFalse();
        assertThat(captchas.consume(session, form.getId(), answer)).isFalse();
    }
    @Test void expiresAtFiveMinuteBoundaryAndCannotMoveBetweenSessions() {
        CaptchaService.IssuedChallenge form = captchas.issue(session);
        String answer = solve(form.getQuestion());
        assertThat(captchas.consume(new MockHttpSession(), form.getId(), answer)).isFalse();
        clock.advance(Duration.ofMinutes(5));
        assertThat(captchas.consume(session, form.getId(), answer)).isFalse();
    }
    @Test void loadingAnotherFormDoesNotReplaceTheFirstChallenge() {
        CaptchaService.IssuedChallenge first = captchas.issue(session);
        CaptchaService.IssuedChallenge second = captchas.issue(session);
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(captchas.consume(session, first.getId(), solve(first.getQuestion()))).isTrue();
        assertThat(captchas.consume(session, second.getId(), solve(second.getQuestion()))).isTrue();
    }
    @Test void missingOrUnknownIdDoesNotConsumeAnotherFormsChallenge() {
        CaptchaService.IssuedChallenge form = captchas.issue(session);
        assertThat(captchas.consume(session, null, solve(form.getQuestion()))).isFalse();
        assertThat(captchas.consume(session, "unknown", solve(form.getQuestion()))).isFalse();
        assertThat(captchas.consume(session, form.getId(), "  " + solve(form.getQuestion()) + "  ")).isTrue();
    }
    @Test void wrongAnswerDoesNotConsumeAnotherFormsChallenge() {
        CaptchaService.IssuedChallenge first = captchas.issue(session);
        CaptchaService.IssuedChallenge second = captchas.issue(session);
        assertThat(captchas.consume(session, first.getId(), "999")).isFalse();
        assertThat(captchas.consume(session, second.getId(), solve(second.getQuestion()))).isTrue();
    }
    @Test void boundsOutstandingChallengesAndExpiresOldForms() {
        CaptchaService.IssuedChallenge oldest = captchas.issue(session);
        for (int i = 0; i < 20; i++) { captchas.issue(session); }
        assertThat(captchas.consume(session, oldest.getId(), solve(oldest.getQuestion()))).isFalse();
        CaptchaService.IssuedChallenge expiring = captchas.issue(session);
        clock.advance(Duration.ofMinutes(5));
        CaptchaService.IssuedChallenge fresh = captchas.issue(session);
        assertThat(captchas.consume(session, expiring.getId(), solve(expiring.getQuestion()))).isFalse();
        assertThat(captchas.consume(session, fresh.getId(), solve(fresh.getQuestion()))).isTrue();
    }
    private String solve(String question) {
        String[] values = question.split(" \\+ ");
        return Integer.toString(Integer.parseInt(values[0]) + Integer.parseInt(values[1]));
    }
}

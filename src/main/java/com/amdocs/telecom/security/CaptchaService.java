package com.amdocs.telecom.security;

import org.springframework.stereotype.Service;
import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CaptchaService {
    private static final String ATTRIBUTE = "telecom.captcha.forms";
    private static final int MAX_CHALLENGES = 20;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    public CaptchaService(Clock clock) { this.clock = clock; }

    public IssuedChallenge issue(HttpSession session) {
        synchronized (session) {
            Map<String, Challenge> challenges = challenges(session);
            Instant now = clock.instant();
            challenges.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiry));
            while (challenges.size() >= MAX_CHALLENGES) {
                challenges.remove(challenges.keySet().iterator().next());
            }
            int first = random.nextInt(9) + 1;
            int second = random.nextInt(9) + 1;
            String id = UUID.randomUUID().toString();
            challenges.put(id, new Challenge(SecurityHash.sha256(Integer.toString(first + second)),
                    now.plusSeconds(300)));
            session.setAttribute(ATTRIBUTE, challenges);
            return new IssuedChallenge(id, first + " + " + second);
        }
    }
    public boolean consume(HttpSession session, String id, String answer) {
        synchronized (session) {
            Map<String, Challenge> challenges = challenges(session);
            Challenge challenge = challenges.remove(id);
            session.setAttribute(ATTRIBUTE, challenges);
            return challenge != null && clock.instant().isBefore(challenge.expiry)
                    && SecurityHash.matches(challenge.hash, answer == null ? null : answer.trim());
        }
    }
    @SuppressWarnings("unchecked")
    private Map<String, Challenge> challenges(HttpSession session) {
        Map<String, Challenge> value = (Map<String, Challenge>) session.getAttribute(ATTRIBUTE);
        return value == null ? new LinkedHashMap<>() : value;
    }
    public static final class IssuedChallenge {
        private final String id;
        private final String question;
        private IssuedChallenge(String id, String question) { this.id = id; this.question = question; }
        public String getId() { return id; }
        public String getQuestion() { return question; }
    }
    private static final class Challenge implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String hash;
        private final Instant expiry;
        private Challenge(String hash, Instant expiry) { this.hash = hash; this.expiry = expiry; }
    }
}

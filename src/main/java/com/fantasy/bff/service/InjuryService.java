package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.InjuriesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Who is currently hurt, among the players a league can roster.
 *
 * <p>Built the same way as {@link RookieService}: it rides along on the cached NHL-side context
 * the splits are built from, so the answer costs nothing beyond identities already held, and a
 * projection service that is unreachable — or switched off, as it is in production — makes the
 * answer unknown rather than making the request fail. It is a marker on a player list, not data
 * the app runs on.
 */
@Service
public class InjuryService {

    private static final Logger log = LoggerFactory.getLogger(InjuryService.class);

    private final PlayerSplitContextProvider contextProvider;

    public InjuryService(PlayerSplitContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    public InjuriesResponse injuries() {
        try {
            return InjuriesResponse.of(contextProvider.context().injuries());
        } catch (RuntimeException e) {
            log.error("Could not read the injury report; answering unknown", e);
            return InjuriesResponse.unknown();
        }
    }
}

package de.adorsys.opba.fintech.impl.service;

import de.adorsys.opba.fintech.impl.config.FintechUiConfig;
import de.adorsys.opba.fintech.impl.controller.RestRequestContext;
import de.adorsys.opba.fintech.impl.database.entities.RedirectUrlsEntity;
import de.adorsys.opba.fintech.impl.database.entities.SessionEntity;
import de.adorsys.opba.fintech.impl.database.repositories.RedirectUrlRepository;
import de.adorsys.opba.fintech.impl.properties.CookieConfigProperties;
import de.adorsys.opba.fintech.impl.tppclients.SessionCookieType;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Optional;
import java.util.UUID;

import static java.util.Collections.singletonList;

@Slf4j
@Service
@Setter
@RequiredArgsConstructor
public class RedirectHandlerService {
    private static final String LOCATION_HEADER = "Location";
    private final FintechUiConfig uiConfig;
    private final RedirectUrlRepository redirectUrlRepository;
    private final AuthorizeService authorizeService;
    private final RestRequestContext restRequestContext;
    private final CookieConfigProperties cookieConfigProperties;

    @Transactional
    public RedirectUrlsEntity registerRedirectStateForSession(final String finTechRedirectCode, final String okPath, final String nokPath) {
        log.debug("ONLY FOR DEBUG: finTechRedirectCode: {}", finTechRedirectCode);

        String localOkPath = okPath.replaceAll("^/", "");
        String localNokPath = nokPath.replaceAll("^/", "");

        RedirectUrlsEntity redirectUrls = new RedirectUrlsEntity();

        redirectUrls.setRedirectCode(finTechRedirectCode);
        redirectUrls.setOkStatePath(localOkPath);
        redirectUrls.setNokStatePath(localNokPath);

        return redirectUrlRepository.save(redirectUrls);
    }

    @Transactional
    public ResponseEntity doRedirect(final String uiGivenAuthId, final String redirectCode) {
        if (StringUtils.isBlank(redirectCode)) {
            log.warn("Validation redirect request was failed: redirect code is empty!");
            return prepareErrorRedirectResponse(uiConfig.getExceptionUrl());
        }

        if (StringUtils.isBlank(uiGivenAuthId)) {
            log.warn("Validation redirect request was failed: authId is empty!");
            return prepareErrorRedirectResponse(uiConfig.getUnauthorizedUrl());
        }

        Optional<RedirectUrlsEntity> redirectUrls = redirectUrlRepository.findByRedirectCode(redirectCode);

        if (!redirectUrls.isPresent()) {
            log.warn("Validation redirect request was failed: redirect code {} is wrong", redirectCode);
            return prepareErrorRedirectResponse(uiConfig.getUnauthorizedUrl());
        }

        if (!authorizeService.isAuthorized()) {
            log.warn("Validation redirect request was failed: Xsrf Token is wrong or user are not authorized!");
            return prepareErrorRedirectResponse(uiConfig.getUnauthorizedUrl());
        }

        redirectUrlRepository.delete(redirectUrls.get());

        SessionEntity sessionEntity = authorizeService.getSession();
        String storedAuthId = sessionEntity.getAuthId();

        if (!uiGivenAuthId.equals(storedAuthId)) {
            log.warn("Validation redirect request was failed: authid expected was {}, but authid from ui was {}", storedAuthId, uiGivenAuthId);
            return prepareErrorRedirectResponse(uiConfig.getUnauthorizedUrl());
        }

        log.info("authId {}", uiGivenAuthId);
        sessionEntity.setConsentConfirmed(true);
        return prepareRedirectToReadResultResponse(sessionEntity, redirectUrls.get());
    }

    private ResponseEntity prepareRedirectToReadResultResponse(SessionEntity sessionEntity, RedirectUrlsEntity redirectUrls) {
        String xsrfToken = UUID.randomUUID().toString();
        HttpHeaders authHeaders = authorizeService.modifySessionEntityAndCreateNewAuthHeader(restRequestContext.getRequestId(), sessionEntity,
                xsrfToken, cookieConfigProperties, SessionCookieType.REGULAR);
        authHeaders.put(LOCATION_HEADER, singletonList(redirectUrls.getOkStatePath()));
        return new ResponseEntity<>(authHeaders, HttpStatus.ACCEPTED);
    }

    private ResponseEntity prepareErrorRedirectResponse(String redirectUrl) {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.put(LOCATION_HEADER, singletonList(redirectUrl));

        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }
}

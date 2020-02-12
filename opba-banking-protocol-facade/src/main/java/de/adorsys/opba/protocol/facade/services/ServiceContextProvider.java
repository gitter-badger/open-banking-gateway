package de.adorsys.opba.protocol.facade.services;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Strings;
import de.adorsys.opba.db.domain.entity.sessions.AuthSession;
import de.adorsys.opba.db.domain.entity.sessions.ServiceSession;
import de.adorsys.opba.db.repository.jpa.AuthenticationSessionRepository;
import de.adorsys.opba.db.repository.jpa.ServiceSessionRepository;
import de.adorsys.opba.protocol.api.dto.context.ServiceContext;
import de.adorsys.opba.protocol.api.dto.request.FacadeServiceableGetter;
import de.adorsys.opba.protocol.api.dto.request.FacadeServiceableRequest;
import de.adorsys.opba.protocol.api.services.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static de.adorsys.opba.protocol.facade.config.EncryptionConfig.ALGO;
import static de.adorsys.opba.protocol.facade.config.EncryptionConfig.ITER_COUNT;
import static de.adorsys.opba.protocol.facade.utils.EncryptionUtils.getNewSalt;

@Service
@RequiredArgsConstructor
public class ServiceContextProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private final AuthenticationSessionRepository authSessions;
    private final ServiceSessionRepository serviceSessions;
    private final EncryptionService encryptionService;

    @Transactional
    @SneakyThrows
    public <T extends FacadeServiceableGetter> ServiceContext<T> provide(T request) {
        FacadeServiceableRequest facadeServiceable = request.getFacadeServiceable();
        if (null == facadeServiceable) {
            throw new IllegalArgumentException("No serviceable body");
        }

        AuthSession authSession = extractAndValidateAuthSession(request);
        ServiceSession session = extractOrCreateServiceSession(request, authSession);

        byte[] decryptedSecretKey = encryptionService.decryptSecretKey(session.getSecretKey());
        byte[] decryptedData = encryptionService.decrypt(session.getContext().getBytes(), decryptedSecretKey);
        FacadeServiceableRequest facadeServiceableDecrypted = MAPPER.readValue(decryptedData, FacadeServiceableRequest.class);

        return ServiceContext.<T>builder()
                .serviceSessionId(session.getId())
                .serviceBankProtocolId(null == authSession ? null : authSession.getParent().getProtocol().getId())
                .authorizationBankProtocolId(null == authSession ? null : authSession.getProtocol().getId())
                .bankId(facadeServiceable.getBankId() == null ? facadeServiceableDecrypted.getBankId() : facadeServiceable.getBankId())
                .authSessionId(null == authSession ? null : authSession.getId())
                // Currently 1-1 auth-session to service session
                .futureAuthSessionId(session.getId())
                .futureRedirectCode(UUID.randomUUID())
                .request(request)
                .authContext(null == authSession ? null : authSession.getContext())
                .fintechRedirectOkUri(session.getFintechOkUri())
                .fintechRedirectNokUri(session.getFintechNokUri())
                .build();
    }

    private <T extends FacadeServiceableGetter> ServiceSession extractOrCreateServiceSession(
            T request,
            AuthSession authSession
    ) {
        if (null != authSession) {
            return authSession.getParent();
        } else {
            return findServiceSessionByIdOrCreate(request);
        }
    }

    @NotNull
    @SneakyThrows
    private <T extends FacadeServiceableGetter> ServiceSession findServiceSessionByIdOrCreate(T request) {
        FacadeServiceableRequest facadeServiceable = request.getFacadeServiceable();
        String sessionPassword = facadeServiceable.getSessionPassword();

        Optional<ServiceSession> existingSession = Optional.empty();
        if (null != facadeServiceable.getServiceSessionId()) {
            existingSession = serviceSessions.findById(facadeServiceable.getServiceSessionId());
        }

        byte[] salt = getNewSalt();
        byte[] key = secretKeyReadFromDbOrGenerate(existingSession, sessionPassword, salt);
        byte[] encryptedKey = encryptionService.encryptSecretKey(key);

        if (existingSession.isPresent()) {
            ServiceSession session = existingSession.get();
            if (Strings.isNullOrEmpty(new String(session.getSecretKey()))) {
                session.setSecretKey(encryptedKey);
            }
            return session;
        }

        ServiceSession session = new ServiceSession();
        if (null != facadeServiceable.getServiceSessionId()) {
            session.setId(facadeServiceable.getServiceSessionId());
        }
        byte[] serializedData = MAPPER.writeValueAsBytes(facadeServiceable);
        byte[] encryptedData = encryptionService.encrypt(serializedData, key);
        session.setContext(new String(encryptedData));
        session.setFintechOkUri(facadeServiceable.getFintechRedirectUrlOk());
        session.setFintechNokUri(facadeServiceable.getFintechRedirectUrlNok());
        session.setSecretKey(encryptedKey);
        session.setAlgo(ALGO);
        session.setSalt(salt);
        session.setIterCount(ITER_COUNT);
        return serviceSessions.save(session);
    }

    private byte[] secretKeyReadFromDbOrGenerate(Optional<ServiceSession> savedSession, String sessionPassword, byte[] salt) {
        if (savedSession.isPresent() && !Strings.isNullOrEmpty(new String(savedSession.get().getSecretKey()))) {
            return savedSession.get().getSecretKey();
        }

        if (Strings.isNullOrEmpty(sessionPassword)) {
            throw new RuntimeException("No password. Can't generate secret key");
        }

        // recreate deleted key from password with parameters from db
        if (savedSession.isPresent()) {
            ServiceSession session = savedSession.get();
            SecretKey key = encryptionService.generateKey(sessionPassword, session.getAlgo(), session.getSalt(), session.getIterCount());
            return key.getEncoded();
        }
        // generate new key with parameters from config
        SecretKey key = encryptionService.generateKey(sessionPassword, ALGO, salt, ITER_COUNT);
        return key.getEncoded();
    }

    @SneakyThrows
    private <T extends FacadeServiceableGetter> AuthSession extractAndValidateAuthSession(
            T request) {
        if (null == request.getFacadeServiceable().getAuthorizationSessionId()) {
            return handleNoAuthSession(request);
        }

        return validateAuthSession(request);
    }

    private <T extends FacadeServiceableGetter> AuthSession handleNoAuthSession(T request) {
        if (!Strings.isNullOrEmpty(request.getFacadeServiceable().getRedirectCode())) {
            throw new IllegalArgumentException("Unexpected redirect code as no auth session is present");
        }

        return null;
    }

    private <T extends FacadeServiceableGetter> AuthSession validateAuthSession(T request) {
        if (Strings.isNullOrEmpty(request.getFacadeServiceable().getRedirectCode())) {
            throw new IllegalArgumentException("Missing redirect code");
        }

        UUID sessionId = UUID.fromString(request.getFacadeServiceable().getAuthorizationSessionId());
        AuthSession session = authSessions.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("No auth session " + sessionId));

        if (!Objects.equals(session.getRedirectCode(), request.getFacadeServiceable().getRedirectCode())) {
            throw new IllegalArgumentException("Wrong redirect code");
        }

        return session;
    }
}

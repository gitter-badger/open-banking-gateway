package de.adorsys.opba.protocol.xs2a.service.xs2a.context;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.adorsys.opba.protocol.api.dto.ValidationIssue;
import de.adorsys.opba.protocol.api.services.scoped.RequestScoped;
import de.adorsys.opba.protocol.api.services.scoped.UsesRequestScoped;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

/**
 * Helper class to be very specific of what to save.
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
// TODO - decide do we need to encrypt these
public class LastViolations implements UsesRequestScoped, RequestScoped {

    private Set<ValidationIssue> violations = new HashSet<>();

    public LastViolations(Set<ValidationIssue> violations) {
        this.violations = violations;
    }

    /**
     * Request scoped services provider for sensitive data.
     */
    @Delegate
    @JsonIgnore
    private RequestScoped requestScoped;
}

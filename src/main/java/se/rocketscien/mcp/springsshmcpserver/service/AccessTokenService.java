package se.rocketscien.mcp.springsshmcpserver.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenEntity;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenRepository;

@Service
@RequiredArgsConstructor
public class AccessTokenService {

    private static final int MAX_COMMENT_LENGTH = 255;

    private final AuthTokenRepository authTokenRepository;

    public List<AuthTokenEntity> list() {
        return authTokenRepository.findAll().stream()
                .sorted(Comparator.comparing(AuthTokenEntity::getId))
                .toList();
    }

    public AuthTokenEntity create(Boolean canEdit, Boolean canExecute, String[] executeOnly, String comment,
                                  Boolean isTokenAdmin) {
        checkCommentLength(comment);

        var entity = new AuthTokenEntity();
        entity.setToken(UUID.randomUUID());
        entity.setCanEdit(Boolean.TRUE.equals(canEdit));
        entity.setCanExecute(Boolean.TRUE.equals(canExecute));
        entity.setExecuteOnly(executeOnly != null ? executeOnly : new String[0]);
        entity.setComment(normalizeComment(comment));
        entity.setIsTokenAdmin(Boolean.TRUE.equals(isTokenAdmin));

        return authTokenRepository.save(entity);
    }

    /**
     * Partial update, same semantics as {@code UpsertAccessTokenTool}: {@code null} leaves a field untouched,
     * an empty {@code comment} clears it. Unknown tokens are the caller's concern, the REST layer rejects them
     * with {@code 404} before reaching this method.
     */
    public AuthTokenEntity update(UUID token, Boolean canEdit, Boolean canExecute, String[] executeOnly, String comment,
                                  Boolean isTokenAdmin) {
        var entity = authTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalStateException("Token vanished between lookup and update: " + token));

        if (canEdit != null) {
            entity.setCanEdit(canEdit);
        }
        if (canExecute != null) {
            entity.setCanExecute(canExecute);
        }
        if (executeOnly != null) {
            entity.setExecuteOnly(executeOnly);
        }
        if (comment != null) {
            checkCommentLength(comment);
            entity.setComment(normalizeComment(comment));
        }
        if (isTokenAdmin != null) {
            entity.setIsTokenAdmin(isTokenAdmin);
        }

        return authTokenRepository.save(entity);
    }

    public void delete(UUID token) {
        authTokenRepository.findByToken(token).ifPresent(authTokenRepository::delete);
    }

    private static void checkCommentLength(String comment) {
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("comment exceeds " + MAX_COMMENT_LENGTH + " characters");
        }
    }

    private static String normalizeComment(String comment) {
        return comment == null || comment.isEmpty() ? null : comment;
    }
}

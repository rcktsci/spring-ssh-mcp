package se.rocketscien.mcp.springsshmcpserver.service;

import org.springframework.stereotype.Service;

import se.rocketscien.mcp.springsshmcpserver.config.AuthException;
import se.rocketscien.mcp.springsshmcpserver.domain.AuthTokenEntity;

@Service
public class AuthService {

    public static final ScopedValue<AuthTokenEntity> CURRENT = ScopedValue.newInstance();

    public void requireRole(String role) {
        var token = CURRENT.isBound() ? CURRENT.get() : null;
        if (token == null) {
            throw new AuthException("Access denied: no authentication");
        }
        if ("EDIT".equals(role) && !Boolean.TRUE.equals(token.getCanEdit())) {
            throw new AuthException("Access denied: missing role " + role);
        }
        if ("EXECUTE".equals(role) && !Boolean.TRUE.equals(token.getCanExecute())) {
            throw new AuthException("Access denied: missing role " + role);
        }
    }

    public boolean canExecuteOnServer(String name) {
        var token = CURRENT.isBound() ? CURRENT.get() : null;
        if (token == null) {
            return false;
        }
        return token.canExecuteOnServer(name);
    }
}

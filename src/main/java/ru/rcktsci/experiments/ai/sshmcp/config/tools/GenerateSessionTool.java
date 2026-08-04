package ru.rcktsci.experiments.ai.sshmcp.config.tools;

import java.security.SecureRandom;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class GenerateSessionTool {

    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Tool(
            name = "generate_session_id",
            description = """
                    Generate a unique session ID for all `execute` tool calls. Pattern: [a-z0-9]{20}.
                    Generate once and reuse the same known ID for all subsequent execute calls within a conversation.
                    """
    )
    public String generateSessionId() {
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) {
            sb.append(CHARSET.charAt(RANDOM.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }
}

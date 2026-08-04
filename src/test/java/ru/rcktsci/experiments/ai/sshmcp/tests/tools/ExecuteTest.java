package ru.rcktsci.experiments.ai.sshmcp.tests.tools;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import ru.rcktsci.experiments.ai.sshmcp.tests.BaseCallToolTest;

import static org.assertj.core.api.Assertions.assertThat;

class ExecuteTest extends BaseCallToolTest {

    @Test
    void basic_success() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "whoami", "session-1", 10));
        assertThat(r.get("stdout").asText().trim()).isEqualTo("root");
        assertThat(r.get("exit_code").asInt()).isEqualTo(0);
        assertThat(r.get("timed_out").asBoolean()).isFalse();
    }

    @Test
    void exit_code_nonzero() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "ls /non/existent/path", "session-2", 10));
        assertThat(r.get("exit_code").asInt()).isNotEqualTo(0);
    }

    @Test
    void command_not_found_exit_127() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "nonexistentcommand", "session-3", 10));
        assertThat(r.get("exit_code").asInt()).isEqualTo(127);
    }

    @Test
    void server_not_found_error() {
        var text = getResponseText(execute("fake-server", "whoami", "session-4", 10));
        assertThat(text).contains("Server not found");
    }

    @Test
    void special_characters_in_output() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "printf 'Hello\\nWorld\\t!'", "session-5", 10));
        assertThat(r.get("stdout").asText()).contains("Hello");
        assertThat(r.get("stdout").asText()).contains("World");
        assertThat(r.get("stdout").asText()).contains("!");
    }

    @Test
    void large_output() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "for i in $(seq 1 100); do echo $i; done", "session-6", 30));
        assertThat(r.get("stdout").asText()).contains("100");
        assertThat(r.get("exit_code").asInt()).isEqualTo(0);
    }

    @Test
    void same_session_id_reuses_connection() {
        addServerConnection("test-ssh");
        var e1 = getExecResult(execute("test-ssh", "echo first", "reuse-session", 10));
        var e2 = getExecResult(execute("test-ssh", "echo second", "reuse-session", 10));
        assertThat(e1.get("stdout").asText().trim()).isEqualTo("first");
        assertThat(e2.get("stdout").asText().trim()).isEqualTo("second");
        assertThat(e1.get("exit_code").asInt()).isEqualTo(0);
        assertThat(e2.get("exit_code").asInt()).isEqualTo(0);
    }

    @Test
    void different_session_ids_different_connections() {
        addServerConnection("test-ssh");
        var e1 = getExecResult(execute("test-ssh", "whoami", "session-a", 10));
        var e2 = getExecResult(execute("test-ssh", "whoami", "session-b", 10));
        assertThat(e1.get("stdout").asText().trim()).isEqualTo("root");
        assertThat(e2.get("stdout").asText().trim()).isEqualTo("root");
    }

    @Test
    void command_times_out_and_returns_partial_output() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "sleep 5 && echo done", "timeout-session", 1));
        assertThat(r.get("timed_out").asBoolean()).isTrue();
        assertThat(r.get("stdout").asText()).doesNotContain("done");
        assertThat(r.get("exit_code").asInt()).isEqualTo(-1);
    }

    @Test
    void timeout_is_saved_to_history() {
        addServerConnection("test-ssh");
        execute("test-ssh", "sleep 10", "timeout-history", 1);
        var r = getExecResult(execute("test-ssh", "echo check", "another-session", 5));
        assertThat(r.get("timed_out").asBoolean()).isFalse();
    }

    @Test
    void fast_command_completes_before_timeout() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "echo hello", "fast-session", 30));
        assertThat(r.get("timed_out").asBoolean()).isFalse();
        assertThat(r.get("stdout").asText().trim()).isEqualTo("hello");
        assertThat(r.get("exit_code").asInt()).isEqualTo(0);
    }

    @Test
    void zero_timeout_treated_as_no_limit() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "echo hello", "zero-timeout", 0));
        assertThat(r.get("timed_out").asBoolean()).isFalse();
        assertThat(r.get("stdout").asText().trim()).isEqualTo("hello");
    }

    @Test
    void negative_timeout_treated_as_default() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "echo hello", "negative-timeout", -5));
        assertThat(r.get("timed_out").asBoolean()).isFalse();
        assertThat(r.get("stdout").asText().trim()).isEqualTo("hello");
    }

    @Test
    void execution_saves_to_history() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "echo saved", "history-test", 10));
        assertThat(r.get("stdout").asText()).contains("saved");
    }

    @SneakyThrows
    @Test
    void execute_with_key_auth() {
        addServerConnection("key-auth-server", SSH_CONTAINER.getMappedPort(22), TEST_PRIVATE_KEY, null);
        var r = getExecResult(execute("key-auth-server", "whoami", "key-session", 10));
        assertThat(r.get("stdout").asText().trim()).isEqualTo("root");
        assertThat(r.get("exit_code").asInt()).isEqualTo(0);
    }

    @SneakyThrows
    @Test
    void execute_with_key_auth_and_passphrase() {
        addServerConnection("key-auth-pass-server", SSH_CONTAINER.getMappedPort(22), TEST_PRIVATE_KEY_WITH_PASSPHRASE, "testpassphrase");
        var r = getExecResult(execute("key-auth-pass-server", "whoami", "key-pass-session", 10));
        assertThat(r.get("stdout").asText().trim()).isEqualTo("root");
        assertThat(r.get("exit_code").asInt()).isEqualTo(0);
    }

    @SneakyThrows
    @Test
    void execute_with_key_wrong_passphrase_fails() {
        addServerConnection("key-wrong-pass", SSH_CONTAINER.getMappedPort(22), TEST_PRIVATE_KEY_WITH_PASSPHRASE, "wrongpassphrase");
        var text = getResponseText(execute("key-wrong-pass", "whoami", "wrong-pass-session", 10));
        assertThat(text).contains("Execution failed");
    }

    @SneakyThrows
    @Test
    void execute_with_invalid_key_format_fails() {
        addServerConnection("invalid-key", SSH_CONTAINER.getMappedPort(22), "not-a-valid-key", null);
        var text = getResponseText(execute("invalid-key", "whoami", "invalid-key-session", 10));
        assertThat(text).contains("Execution failed");
    }

    @Test
    void execute_with_exit_code() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "exit 42", "s1", 10));
        assertThat(r.get("exit_code").asInt()).isEqualTo(42);
    }

    @Test
    void execute_with_stdout() {
        addServerConnection("test-ssh");
        var r = getExecResult(execute("test-ssh", "printf 'line1\\nline2\\n'", "s1", 10));
        assertThat(r.get("stdout").asText()).contains("line1");
        assertThat(r.get("stdout").asText()).contains("line2");
        assertThat(r.get("exit_code").asInt()).isEqualTo(0);
    }

    @Test
    void execute_without_auth_returns_error() {
        var response = execute("test-ssh", "echo hello", "s1", 30, null);
        assertThat(getResponseText(response)).contains("Access denied");
    }

    @Test
    void execute_without_execute_role_returns_error() {
        var response = execute("test-ssh", "echo hello", "s1", 30, EDIT_ONLY_TOKEN);
        assertThat(getResponseText(response)).contains("Access denied");
    }

    @Test
    void execute_with_execute_role_succeeds() {
        addServerConnection("test-ssh");
        var response = execute("test-ssh", "echo hello", "s1", 30, EXECUTE_ONLY_TOKEN);
        assertThat(getResponseText(response)).contains("hello");
    }

    @Test
    void execute_server_not_in_execute_only_returns_error() {
        addServerConnection("other-server");
        var response = execute("other-server", "echo hello", "s1", 30, EXECUTE_ONLY_TOKEN);
        assertThat(getResponseText(response)).contains("Server not allowed");
    }

    @Test
    void execute_with_wildcard_pattern_succeeds() {
        addServerConnection("vm/staging");
        var response = execute("vm/staging", "echo hello", "s1", 30, WILDCARD_TOKEN);
        assertThat(getResponseText(response)).contains("hello");
    }

    @Test
    void execute_with_wildcard_pattern_non_matching_returns_error() {
        addServerConnection("pve/prod");
        var response = execute("pve/prod", "echo hello", "s1", 30, WILDCARD_TOKEN);
        assertThat(getResponseText(response)).contains("Server not allowed");
    }
}

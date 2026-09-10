package com.aonfine.adaworker.claude;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class ClaudeCliInvokerTest {

    private ClaudeCliInvoker.ClaudeConfig config() {
        ClaudeCliInvoker.ClaudeConfig c = new ClaudeCliInvoker.ClaudeConfig();
        c.model = "sonnet";
        c.maxBudgetUsd = 0.5;
        return c;
    }

    @Test public void commandNeverRequestsUnlimitedPermissionBypass() {
        ClaudeCliInvoker invoker = new ClaudeCliInvoker(config());
        List<String> command = invoker.buildCommand("prompt");
        for (String arg : command) {
            assertFalse("must never pass a permission-bypass flag: " + arg,
                    arg.contains("dangerously-skip-permissions"));
        }
        assertTrue(command.contains("--tools"));
        assertEquals("", command.get(command.indexOf("--tools") + 1));
        assertTrue(command.contains("--max-budget-usd"));
        assertTrue(command.contains("--no-session-persistence"));
        assertTrue(command.contains("--strict-mcp-config"));
        assertTrue(command.contains("--model"));
        assertEquals("sonnet", command.get(command.indexOf("--model") + 1));
    }

    @Test public void promptTellsTheModelEvidenceIsUntrustedAndIncludesTheEvidenceVerbatim() {
        String prompt = ClaudeCliInvoker.buildPrompt("보고서를 작성하세요", "{\"heap\":1}");
        assertTrue(prompt.contains("신뢰할 수 없는"));
        assertTrue(prompt.contains("{\"heap\":1}"));
        assertTrue(prompt.contains("보고서를 작성하세요"));
    }

    @Test public void parsesSuccessfulJsonResult() {
        ClaudeCliInvoker invoker = new ClaudeCliInvoker(config());
        ClaudeCliInvoker.ClaudeResult result = new ClaudeCliInvoker.ClaudeResult();
        result.exitCode = 0;
        result.rawStdout = "{\"type\":\"result\",\"is_error\":false,\"result\":\"# 보고서\\n내용\",\"total_cost_usd\":0.012}";
        invoker.parseInto(result, "");
        assertFalse(result.isError);
        assertEquals("# 보고서\n내용", result.resultText);
        assertEquals(0.012, result.totalCostUsd, 0.0001);
    }

    @Test public void nonZeroExitCodeIsAlwaysAnErrorRegardlessOfStdout() {
        ClaudeCliInvoker invoker = new ClaudeCliInvoker(config());
        ClaudeCliInvoker.ClaudeResult result = new ClaudeCliInvoker.ClaudeResult();
        result.exitCode = 1;
        result.rawStdout = "{\"result\":\"looks fine\"}";
        invoker.parseInto(result, "auth error");
        assertTrue(result.isError);
        assertTrue(result.parseErrorMessage.contains("exited with code 1"));
    }

    @Test public void missingResultFieldIsTreatedAsError() {
        ClaudeCliInvoker invoker = new ClaudeCliInvoker(config());
        ClaudeCliInvoker.ClaudeResult result = new ClaudeCliInvoker.ClaudeResult();
        result.exitCode = 0;
        result.rawStdout = "{\"type\":\"result\",\"is_error\":false}";
        invoker.parseInto(result, "");
        assertTrue(result.isError);
        assertNull(result.resultText);
    }

    @Test public void malformedJsonIsTreatedAsErrorNotAThrownException() {
        ClaudeCliInvoker invoker = new ClaudeCliInvoker(config());
        ClaudeCliInvoker.ClaudeResult result = new ClaudeCliInvoker.ClaudeResult();
        result.exitCode = 0;
        result.rawStdout = "not json at all {{{";
        invoker.parseInto(result, "");
        assertTrue(result.isError);
        assertNotNull(result.parseErrorMessage);
    }

    @Test public void claudeReportedIsErrorTrueIsHonored() {
        ClaudeCliInvoker invoker = new ClaudeCliInvoker(config());
        ClaudeCliInvoker.ClaudeResult result = new ClaudeCliInvoker.ClaudeResult();
        result.exitCode = 0;
        result.rawStdout = "{\"is_error\":true,\"result\":\"budget exceeded\"}";
        invoker.parseInto(result, "");
        assertTrue(result.isError);
    }
}

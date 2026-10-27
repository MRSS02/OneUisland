package dev.flexy.dynamicisland.xposed;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

final class ClaudeSession {
    private static final long HISTORY_MS = 90_000;

    private ClaudeSession() {
    }

    static boolean relevant(String type, boolean hasSession) {
        if ("control_request".equals(type)) return true;
        if (!hasSession) return false;
        return "assistant".equals(type) || "user".equals(type) || "result".equals(type) || "system".equals(type);
    }

    static void onEvent(JSONObject o) {
        if (old(o)) return;
        switch (o.optString("type")) {
            case "assistant":
                onAssistant(o.optJSONObject("message"));
                break;
            case "user":
                onUser(o.optJSONObject("message"));
                break;
            case "result":
                ClaudeReporter.finished(!o.optBoolean("is_error"));
                break;
            case "control_request":
                onControl(o.optJSONObject("request"));
                break;
            case "system":
                onSystem(o);
                break;
            default:
                break;
        }
    }

    private static boolean old(JSONObject o) {
        String ts = o.optString("timestamp", "");
        if (ts.isEmpty()) return false;
        try {
            long t = java.time.Instant.parse(ts).toEpochMilli();
            return System.currentTimeMillis() - t > HISTORY_MS;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void onAssistant(JSONObject message) {
        JSONArray content = message == null ? null : message.optJSONArray("content");
        if (content == null) return;
        ClaudeReporter.working();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) continue;
            switch (block.optString("type")) {
                case "thinking":
                case "redacted_thinking":
                    ClaudeReporter.stage("think", ClaudeReporter.label("Thinking", "Думает"));
                    break;
                case "text":
                    ClaudeReporter.stage("text", firstSentence(block.optString("text")));
                    break;
                case "tool_use":
                    JSONObject input = block.optJSONObject("input");
                    if (input != null && input.optBoolean("run_in_background")) {
                        ClaudeReporter.taskStarted(block.optString("id", null));
                    }
                    onTool(block.optString("name"), input);
                    break;
                default:
                    break;
            }
        }
    }

    private static void onUser(JSONObject message) {
        if (message == null) return;
        Object content = message.opt("content");
        if (content instanceof String) {
            ClaudeReporter.working();
            ClaudeReporter.stage("user", ClaudeReporter.label("Reading your message", "Читает ваше сообщение"));
            return;
        }
        if (!(content instanceof JSONArray)) return;
        JSONArray arr = (JSONArray) content;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject b = arr.optJSONObject(i);
            if (b != null && "text".equals(b.optString("type"))) {
                ClaudeReporter.working();
                ClaudeReporter.stage("user", ClaudeReporter.label("Reading your message", "Читает ваше сообщение"));
                return;
            }
        }
    }

    private static void onSystem(JSONObject o) {
        if (!"task_notification".equals(o.optString("subtype"))) return;
        String status = o.optString("status");
        if ("running".equals(status) || "started".equals(status)) {
            ClaudeReporter.taskStarted(o.optString("tool_use_id", null));
        } else {
            ClaudeReporter.taskEnded(o.optString("tool_use_id", null));
        }
    }

    private static void onControl(JSONObject request) {
        if (request == null || !"can_use_tool".equals(request.optString("subtype"))) return;
        String tool = request.optString("tool_name");
        JSONObject input = request.optJSONObject("input");
        String what = input == null ? null : first(input, "description", "command", "file_path", "url");
        String text = ClaudeReporter.label("Allow ", "Разрешить ") + tool + (what != null ? ": " + what : "") + "?";
        ClaudeReporter.ask(text, true);
    }

    private static void onTool(String name, JSONObject in) {
        if (name == null) return;
        if (in == null) in = new JSONObject();
        String n = name.toLowerCase(Locale.ROOT);
        if ("askuserquestion".equals(n)) {
            JSONArray qs = in.optJSONArray("questions");
            JSONObject q = qs != null && qs.length() > 0 ? qs.optJSONObject(0) : null;
            String text = q != null ? first(q, "question", "header") : first(in, "question");
            ClaudeReporter.stage("ask", ClaudeReporter.label("Asking you", "Задаёт вопрос"));
            ClaudeReporter.ask(text, false);
            return;
        }
        if (n.endsWith("wrap_up")) {
            String ask = first(in, "ask");
            if (ask != null) ClaudeReporter.pendingQuestion(ask);
            return;
        }
        if (n.endsWith("update_plan") || n.equals("toolsearch") || n.equals("todowrite")) return;
        ClaudeReporter.stage(toolKind(n), toolLabel(n, name, in));
    }

    private static String toolKind(String n) {
        if (n.equals("bash") || n.equals("powershell") || n.equals("monitor")) return "cmd";
        if (n.equals("read")) return "read";
        if (n.equals("edit") || n.equals("multiedit") || n.equals("write") || n.equals("notebookedit")) return "edit";
        if (n.equals("grep") || n.equals("glob")) return "search";
        if (n.contains("websearch") || n.contains("webfetch") || n.contains("browser")) return "web";
        if (n.equals("agent") || n.equals("task") || n.equals("workflow")) return "agent";
        if (n.contains("artifact")) return "artifact";
        return "tool";
    }

    private static String toolLabel(String n, String name, JSONObject in) {
        String file = baseName(first(in, "file_path", "notebook_path", "path"));
        if (n.equals("bash") || n.equals("powershell")) {
            String d = first(in, "description");
            return d != null ? d : ClaudeReporter.label("Running a command", "Запускает команду");
        }
        if (n.equals("read")) return ClaudeReporter.label("Reading ", "Читает ") + (file != null ? file : "");
        if (n.equals("edit") || n.equals("multiedit") || n.equals("write") || n.equals("notebookedit")) {
            return ClaudeReporter.label("Editing ", "Редактирует ") + (file != null ? file : "");
        }
        if (n.equals("grep")) {
            String p = first(in, "pattern");
            return ClaudeReporter.label("Searching code", "Ищет в коде") + (p != null && p.length() < 40 ? " · " + p : "");
        }
        if (n.equals("glob")) return ClaudeReporter.label("Looking for files", "Ищет файлы");
        if (n.contains("websearch") || n.contains("webfetch") || n.contains("browser")) {
            return ClaudeReporter.label("Searching the web", "Ищет в интернете");
        }
        if (n.equals("agent") || n.equals("task") || n.equals("workflow")) {
            String d = first(in, "description");
            return ClaudeReporter.label("Running an agent", "Запускает агента") + (d != null ? " · " + d : "");
        }
        if (n.contains("artifact")) return ClaudeReporter.label("Publishing a page", "Публикует страницу");
        String clean = name.startsWith("mcp__") ? name.substring(name.lastIndexOf("__") + 2) : name;
        return ClaudeReporter.label("Using ", "Использует ") + clean.replace('_', ' ');
    }

    private static String firstSentence(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;
        int nl = t.indexOf('\n');
        if (nl > 0) t = t.substring(0, nl);
        t = t.replaceAll("[*_`#>]+", "").trim();
        for (int i = 20; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == ':') && (i + 1 == t.length() || t.charAt(i + 1) == ' ')) {
                return t.substring(0, c == ':' ? i : i + 1);
            }
        }
        return t;
    }

    private static String baseName(String path) {
        if (path == null) return null;
        int i = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return i >= 0 ? path.substring(i + 1) : path;
    }

    private static String first(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (!v.isEmpty()) return v;
        }
        return null;
    }
}

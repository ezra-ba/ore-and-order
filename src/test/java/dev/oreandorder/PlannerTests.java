package dev.oreandorder;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real HTTP requests against a loopback fixture; no credentials or external services. */
public final class PlannerTests {
    private static int checks;
    private static final String PLAN = "{\"task\":\"acquire\",\"item\":\"minecraft:iron_ingot\",\"quantity\":16,\"deliver_to\":\"base_storage\"}";

    public static void main(String[] args) throws Exception {
        check(TaskPlan.parse(PLAN, item -> item.equals("minecraft:iron_ingot")).quantity() == 16, "valid plan");
        reject(PLAN.replace("16", "0")); reject(PLAN.replace("16", "4097"));
        reject(PLAN.replace("16", "1.5")); reject(PLAN.replace("16", "\"16\""));
        reject(PLAN.replace("acquire", "execute_command"));
        reject(PLAN.replace("base_storage", "somewhere_else"));
        reject(PLAN.replace("iron_ingot", "imaginary_item"));
        reject(PLAN.replace("16", "true")); reject("```json\n" + PLAN + "\n```");
        reject(PLAN.replace("{", "{\"command\":\"op player\",")); reject("{\"task\":\"unsupported\"}");
        try { new PlannerConfig(URI.create("http://example.com/v1/chat/completions"), "test", "KEY", Duration.ofSeconds(1));
            throw new AssertionError("remote HTTP accepted"); } catch (IllegalArgumentException expected) { checks++; }

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService fixtureThreads = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(fixtureThreads);
        AtomicInteger status = new AtomicInteger(200);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            JsonObject request = JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            check(request.get("model").getAsString().equals("fixture-model"), "model configuration");
            check(!request.get("stream").getAsBoolean(), "non-streaming");
            check(request.getAsJsonArray("messages").size() == 2, "system and user messages");
            check("Bearer test-secret".equals(exchange.getRequestHeaders().getFirst("Authorization")), "authorization header");
            int code = status.get();
            String response = envelope(PLAN, "stop");
            if (code == 201) { code = 200; response = "not json"; }
            if (code == 202) { code = 200; response = envelope(PLAN, "length"); }
            if (code == 203) { code = 200; response = "x".repeat(70000); }
            if (code == 204) {
                code = 200;
                try { Thread.sleep(600); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            if (code >= 400) response = "test-secret should never be surfaced";
            if (code == 302) exchange.getResponseHeaders().set("Location", "/redirected");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(code, bytes.length); exchange.getResponseBody().write(bytes); }
            catch (java.io.IOException ignored) { /* Client timeout or response-size rejection. */ }
            finally { exchange.close(); }
        });
        server.start();
        var endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
        try (PlannerClient client = new PlannerClient(new PlannerConfig(endpoint, "fixture-model", "TEST_KEY", Duration.ofSeconds(3)))) {
            check(client.plan("Get 16 iron", "test-secret").get(5, TimeUnit.SECONDS).equals(PLAN), "HTTP round trip");
            for (int code : new int[]{401, 403, 429, 500, 302, 201, 202, 203}) {
                status.set(code);
                expectFailure(client.plan("Get 16 iron", "test-secret"), "provider failure " + code);
            }
            int before = calls.get();
            expectFailure(client.plan("Get iron", ""), "missing key");
            expectFailure(client.plan("x".repeat(501), "test-secret"), "oversized input");
            check(calls.get() == before, "invalid input never reaches provider");
        }
        status.set(204);
        try (PlannerClient client = new PlannerClient(new PlannerConfig(endpoint, "fixture-model", "TEST_KEY", Duration.ofMillis(150)))) {
            expectFailure(client.plan("Get iron", "test-secret"), "timeout");
        } finally { server.stop(0); fixtureThreads.shutdownNow(); }
        System.out.println("Passed " + checks + " checks (task validation and real local HTTP).");
    }

    private static String envelope(String plan, String finish) {
        JsonObject message = new JsonObject(); message.addProperty("content", plan);
        JsonObject choice = new JsonObject(); choice.add("message", message); choice.addProperty("finish_reason", finish);
        JsonArray choices = new JsonArray(); choices.add(choice);
        JsonObject response = new JsonObject(); response.add("choices", choices); return response.toString();
    }
    private static void reject(String json) {
        try { TaskPlan.parse(json, item -> item.equals("minecraft:iron_ingot")); throw new AssertionError("Accepted invalid plan"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void expectFailure(CompletableFuture<String> future, String name) throws Exception {
        try { future.get(5, TimeUnit.SECONDS); throw new AssertionError("Expected " + name); }
        catch (ExecutionException expected) {
            check(expected.getCause() instanceof PlannerClient.PlannerException, name);
            check(!expected.getCause().getMessage().contains("test-secret"), "no secret disclosure");
        }
    }
    private static synchronized void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name); checks++;
    }
}

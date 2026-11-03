package dev.oreandorder;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Explicit opt-in smoke test. Sends one fixed, non-sensitive order to NVIDIA. */
public final class LivePlannerTest {
    public static void main(String[] args) throws Exception {
        var config = new PlannerConfig(URI.create("https://integrate.api.nvidia.com/v1/chat/completions"),
                PlannerConfig.DEFAULT_MODEL, "NVIDIA_API_KEY", Duration.ofSeconds(45));
        try (PlannerClient client = new PlannerClient(config)) {
            TaskPlan plan = TaskPlan.parse(client.plan("Get 16 iron ingots and deliver them to base storage.")
                    .get(50, TimeUnit.SECONDS), item -> item.equals("minecraft:iron_ingot"));
            if (plan.quantity() != 16) throw new AssertionError("Incorrect quantity");
            System.out.println("Live NVIDIA check passed: " + plan.quantity() + " x " + plan.item() + " -> " + plan.deliverTo());
        }
    }
}

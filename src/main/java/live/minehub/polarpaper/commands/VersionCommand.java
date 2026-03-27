package live.minehub.polarpaper.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.util.ExceptionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VersionCommand extends PolarCmd {

    private static final long CHECK_INTERVAL = 3 * 3_600_000L; // 3 hour in millis
    private static long LAST_UPDATED = -1L;
    private static @Nullable GithubRelease CACHED_RELEASE = null;

    public VersionCommand() {
        super("version", "Display the plugin version");
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        return run(ctx);
    }

    protected static int run(CommandContext<CommandSourceStack> ctx) {
        String currentVersion = PolarPaper.getPlugin().getPluginMeta().getVersion();

        ctx.getSource().getSender().sendMessage(
                Component.text()
                        .append(Component.text("❄ Polar for Paper v", NamedTextColor.AQUA))
                        .append(Component.text(currentVersion, NamedTextColor.AQUA))
        );

        getLatestReleaseCached().thenAccept(release -> {
            if (release == null) return;

            String downloadUrl = release.assets.getFirst().browser_download_url;

            if (currentVersion.equals(release.name)) {
                ctx.getSource().getSender().sendMessage(Component.text("You are on the latest version!", NamedTextColor.GREEN));
            } else {
                ctx.getSource().getSender().sendMessage(Component.text()
                        .append(Component.text("You are not on the latest version!\n", NamedTextColor.RED))
                        .append(Component.text("Latest version: v" + release.name, NamedTextColor.AQUA))
                        .append(Component.text(" (Released " + release.updated_at_relative + ") ", NamedTextColor.GRAY))
                        .append(Component.text("Download", NamedTextColor.BLUE, TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.openUrl(downloadUrl))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to download"))))
                        .build()
                );
            }
        }).exceptionally(e -> {
            ExceptionUtil.log(e);
            return null;
        });

        return Command.SINGLE_SUCCESS;
    }

    public static String toRelative(String isoTime) {
        Instant pastInstant = Instant.parse(isoTime);
        Instant nowInstant = Instant.now();

        ZoneId zone = ZoneId.systemDefault();

        ZonedDateTime past = pastInstant.atZone(zone);
        ZonedDateTime now = nowInstant.atZone(zone);

        ChronoUnit[] values = ChronoUnit.values();
        for (int i = values.length - 1; i >= 0; i--) {
            ChronoUnit unit = values[i];
            if (unit == ChronoUnit.HALF_DAYS || unit == ChronoUnit.FOREVER) continue;
            long amount = unit.between(past, now);
            if (amount > 0) return amount + " " + unit.toString().toLowerCase() + " ago";
        }

        return "now";
    }

    private static CompletableFuture<@Nullable GithubRelease> getLatestReleaseCached() {
        long lastCheck = System.currentTimeMillis() - LAST_UPDATED;
        if (CACHED_RELEASE == null || lastCheck > CHECK_INTERVAL) {
            return getLatestRelease();
        }
        return CompletableFuture.completedFuture(CACHED_RELEASE);
    }

    private static CompletableFuture<@Nullable GithubRelease> getLatestRelease() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/MinehubMC/PolarPaper/releases?per_page=2"))
                    .build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        Gson gson = new Gson();
                        Type listOfGithubRelease = new TypeToken<List<GithubRelease>>() {
                        }.getType();
                        List<GithubRelease> releases = gson.fromJson(body, listOfGithubRelease);
                        for (GithubRelease release : releases) {
                            if (!release.prerelease) {
                                release.updated_at_relative = toRelative(release.updated_at);
                                CACHED_RELEASE = release;
                                LAST_UPDATED = System.currentTimeMillis();
                                return release;
                            }
                        }
                        return null;
                    }).exceptionally(e -> {
                        ExceptionUtil.log(e);
                        return null;
                    });
        } catch (Exception e) {
            ExceptionUtil.log(e);
            return null;
        }
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {

    }

    private static class GithubRelease {
        String name;
        boolean prerelease;
        String updated_at;
        String updated_at_relative;
        List<GithubAsset> assets;
    }
    private static class GithubAsset {
        String browser_download_url;
    }

}

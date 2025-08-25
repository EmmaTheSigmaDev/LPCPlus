package me.wikmor.lpcplus;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.BiFunction;

public final class LPCPlus extends JavaPlugin implements Listener {

	private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

	private LuckPerms luckPerms;
	private boolean paperPresent;

	@Override
	public void onEnable() {
		this.luckPerms = getServer().getServicesManager().load(LuckPerms.class);
		saveDefaultConfig();
		getServer().getPluginManager().registerEvents(this, this);

		try {
			Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
			paperPresent = true;
			getLogger().info("✅ Paper detected, Adventure per-viewer chat enabled.");
		} catch (ClassNotFoundException e) {
			paperPresent = false;
			getLogger().info("ℹ Running on Spigot, legacy chat only.");
		}
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
		if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
			reloadConfig();
			sender.sendMessage(colorize("&aLPCPlus has been reloaded."));
			return true;
		}
		return false;
	}

	@Override
	public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
		if (args.length == 1) return Collections.singletonList("reload");
		return Collections.emptyList();
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		CachedMetaData metaData = luckPerms.getPlayerAdapter(Player.class).getMetaData(player);
		String group = metaData.getPrimaryGroup();
		String format = buildFormat(player, metaData, group);
		String message = applyMessageColors(player, event.getMessage());

		if (!paperPresent) {
			// Spigot fallback
			event.setFormat(format.replace("{message}", message).replace("%", "%%"));
			return;
		}

		// Paper Adventure per-viewer rendering
		try {
			Object asyncChatEvent = Class.forName("io.papermc.paper.event.player.AsyncChatEvent")
					.cast(event);

			// Use Adventure renderer lambda (per viewer)
			BiFunction<Player, Component, Component> renderer = (viewer, msg) -> {
				String perViewerFormat = format;
				if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
					perViewerFormat = PlaceholderAPI.setPlaceholders(viewer, perViewerFormat);
				}
				String perViewerMessage = applyMessageColors(player, event.getMessage());
				perViewerFormat = perViewerFormat.replace("{message}", perViewerMessage);
				return LegacyComponentSerializer.legacySection().deserialize(perViewerFormat);
			};

			asyncChatEvent.getClass()
					.getMethod("renderer", BiFunction.class)
					.invoke(asyncChatEvent, renderer);

		} catch (Exception ignored) {
			// fails silently on Spigot
		}
	}

	private String buildFormat(Player player, CachedMetaData metaData, String group) {
		String format = getConfig().getString(getConfig().getString("group-formats." + group) != null ?
				"group-formats." + group : "chat-format");

		if (format == null) format = "{name}: {message}";

		format = format
				.replace("{prefix}", Optional.ofNullable(metaData.getPrefix()).orElse(""))
				.replace("{suffix}", Optional.ofNullable(metaData.getSuffix()).orElse(""))
				.replace("{prefixes}", String.join("", metaData.getPrefixes().values()))
				.replace("{suffixes}", String.join("", metaData.getSuffixes().values()))
				.replace("{world}", player.getWorld().getName())
				.replace("{name}", player.getName())
				.replace("{displayname}", player.getDisplayName())
				.replace("{username-color}", Optional.ofNullable(metaData.getMetaValue("username-color")).orElse(""))
				.replace("{message-color}", Optional.ofNullable(metaData.getMetaValue("message-color")).orElse(""));

		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			format = PlaceholderAPI.setPlaceholders(player, format);
		}

		return colorize(translateHexColorCodes(format));
	}

	private String applyMessageColors(Player player, String message) {
		if (player.hasPermission("lpcplus.colorcodes") && player.hasPermission("lpcplus.rgbcodes")) {
			return colorize(translateHexColorCodes(message));
		} else if (player.hasPermission("lpcplus.colorcodes")) {
			return colorize(message);
		} else if (player.hasPermission("lpcplus.rgbcodes")) {
			return translateHexColorCodes(message);
		} else {
			return message;
		}
	}

	private String colorize(final String message) {
		return ChatColor.translateAlternateColorCodes('&', message);
	}

	private String translateHexColorCodes(final String message) {
		final char colorChar = ChatColor.COLOR_CHAR;
		final Matcher matcher = HEX_PATTERN.matcher(message);
		final StringBuffer buffer = new StringBuffer(message.length() + 4 * 8);

		while (matcher.find()) {
			final String group = matcher.group(1);
			matcher.appendReplacement(buffer, colorChar + "x"
					+ colorChar + group.charAt(0) + colorChar + group.charAt(1)
					+ colorChar + group.charAt(2) + colorChar + group.charAt(3)
					+ colorChar + group.charAt(4) + colorChar + group.charAt(5));
		}
		return matcher.appendTail(buffer).toString();
	}
}
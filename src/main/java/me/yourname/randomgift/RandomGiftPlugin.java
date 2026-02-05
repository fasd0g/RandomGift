package me.yourname.randomgift;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomGiftPlugin extends JavaPlugin {

    private enum FullInventoryMode { DROP, SKIP }

    private final ThreadLocalRandom rng = ThreadLocalRandom.current();
    private final List<Material> itemPool = new ArrayList<>();

    private long intervalTicks;
    private long intervalMinutes;

    private int minOnline;
    private int minAmount;
    private int maxAmount;

    private boolean warningEnabled;
    private int warningMinutesBefore;

    private FullInventoryMode fullInventoryMode;

    private int giftTaskId = -1;
    private int warnTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAll();
        reschedule();
        getLogger().info("RandomGift enabled. Pool size: " + itemPool.size());
    }

    @Override
    public void onDisable() {
        cancelTasks();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("randomgift")) return false;

        if (!sender.hasPermission("randomgift.use")) {
            sender.sendMessage(color("&cНет прав: randomgift.use"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(color("&eИспользование: /" + label + " <reload|now|status|add|remove|list>"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("randomgift.reload")) {
                    sender.sendMessage(color("&cНет прав: randomgift.reload"));
                    return true;
                }
                reloadAll();
                reschedule();
                sender.sendMessage(color("&aКонфиг перезагружен. Пул: &e" + itemPool.size() + "&a, интервал: &e" + intervalMinutes + " мин&a."));
                return true;
            }

            case "now" -> {
                if (!sender.hasPermission("randomgift.now")) {
                    sender.sendMessage(color("&cНет прав: randomgift.now"));
                    return true;
                }
                boolean ok = giveRandomGift(true);
                sender.sendMessage(color(ok ? "&aПодарок выдан." : "&eНедостаточно игроков онлайн для выдачи."));
                return true;
            }

            case "status" -> {
                if (!sender.hasPermission("randomgift.status")) {
                    sender.sendMessage(color("&cНет прав: randomgift.status"));
                    return true;
                }
                sender.sendMessage(color("&6RandomGift статус:"));
                sender.sendMessage(color("&7- Интервал: &e" + intervalMinutes + " мин"));
                sender.sendMessage(color("&7- Минимум онлайн: &e" + minOnline));
                sender.sendMessage(color("&7- Кол-во: &e" + minAmount + "&7..&e" + maxAmount));
                sender.sendMessage(color("&7- Предупреждение: &e" + warningEnabled + (warningEnabled ? " (&eза " + warningMinutesBefore + " мин&7)" : "")));
                sender.sendMessage(color("&7- Full inventory: &e" + fullInventoryMode));
                sender.sendMessage(color("&7- Пул предметов: &e" + itemPool.size()));
                return true;
            }

            case "add" -> {
                if (!sender.hasPermission("randomgift.items.add")) {
                    sender.sendMessage(color("&cНет прав: randomgift.items.add"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(color("&eИспользование: /" + label + " add WHITELIST <MATERIAL>"));
                    return true;
                }
                if (!args[1].equalsIgnoreCase("WHITELIST")) {
                    sender.sendMessage(color("&cСейчас поддерживается только WHITELIST."));
                    return true;
                }

                Material mat = parseMaterial(args[2]);
                if (mat == null) {
                    sender.sendMessage(color("&cНеверный предмет: &e" + args[2]));
                    return true;
                }

                List<String> wl = new ArrayList<>(getConfig().getStringList("items.whitelist"));
                boolean exists = wl.stream().anyMatch(s -> s.equalsIgnoreCase(mat.name()));
                if (exists) {
                    sender.sendMessage(color("&e" + mat.name() + " уже есть в WHITELIST."));
                    return true;
                }

                wl.add(mat.name());
                getConfig().set("items.whitelist", wl);
                saveConfig();

                reloadAll();
                sender.sendMessage(color("&aДобавлено: &e" + mat.name() + "&a. Пул теперь: &e" + itemPool.size()));
                return true;
            }

            case "remove" -> {
                if (!sender.hasPermission("randomgift.items.remove")) {
                    sender.sendMessage(color("&cНет прав: randomgift.items.remove"));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(color("&eИспользование: /" + label + " remove WHITELIST <MATERIAL>"));
                    return true;
                }
                if (!args[1].equalsIgnoreCase("WHITELIST")) {
                    sender.sendMessage(color("&cСейчас поддерживается только WHITELIST."));
                    return true;
                }

                Material mat = parseMaterial(args[2]);
                if (mat == null) {
                    sender.sendMessage(color("&cНеверный предмет: &e" + args[2]));
                    return true;
                }

                List<String> wl = new ArrayList<>(getConfig().getStringList("items.whitelist"));
                int before = wl.size();
                wl.removeIf(s -> s.equalsIgnoreCase(mat.name()));

                if (wl.size() == before) {
                    sender.sendMessage(color("&e" + mat.name() + " нет в WHITELIST."));
                    return true;
                }

                getConfig().set("items.whitelist", wl);
                saveConfig();

                reloadAll();
                sender.sendMessage(color("&aУдалено: &e" + mat.name() + "&a. Пул теперь: &e" + itemPool.size()));
                return true;
            }

            case "list" -> {
                if (!sender.hasPermission("randomgift.items.list")) {
                    sender.sendMessage(color("&cНет прав: randomgift.items.list"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(color("&eИспользование: /" + label + " list <WHITELIST|POOL>"));
                    return true;
                }
                String what = args[1].toUpperCase(Locale.ROOT);

                if (what.equals("WHITELIST")) {
                    List<String> wl = getConfig().getStringList("items.whitelist");
                    sender.sendMessage(color("&6WHITELIST (&e" + wl.size() + "&6):"));
                    sender.sendMessage(color("&7" + String.join(", ", wl)));
                    return true;
                }

                if (what.equals("POOL")) {
                    sender.sendMessage(color("&6POOL (&e" + itemPool.size() + "&6): &7" + joinPool(60)));
                    return true;
                }

                sender.sendMessage(color("&cНужно указать WHITELIST или POOL."));
                return true;
            }

            default -> {
                sender.sendMessage(color("&eИспользование: /" + label + " <reload|now|status|add|remove|list>"));
                return true;
            }
        }
    }

    private void reschedule() {
        cancelTasks();

        giftTaskId = Bukkit.getScheduler()
                .runTaskTimer(this, () -> giveRandomGift(true), intervalTicks, intervalTicks)
                .getTaskId();

        if (warningEnabled && warningMinutesBefore > 0) {
            long warnOffsetTicks = warningMinutesBefore * 60L * 20L;
            long warnDelay = Math.max(1L, intervalTicks - Math.min(intervalTicks, warnOffsetTicks));

            warnTaskId = Bukkit.getScheduler()
                    .runTaskTimer(this, this::broadcastWarning, warnDelay, intervalTicks)
                    .getTaskId();
        }
    }

    private void cancelTasks() {
        if (giftTaskId != -1) {
            Bukkit.getScheduler().cancelTask(giftTaskId);
            giftTaskId = -1;
        }
        if (warnTaskId != -1) {
            Bukkit.getScheduler().cancelTask(warnTaskId);
            warnTaskId = -1;
        }
    }

    private void broadcastWarning() {
        if (Bukkit.getOnlinePlayers().size() < minOnline) return;

        String msg = color(getConfig().getString(
                "warning.message",
                "&6[Подарок] &eЧерез {minutes} минут &7будет раздача случайного предмета!"
        ));

        msg = msg.replace("{minutes}", Integer.toString(warningMinutesBefore));
        Bukkit.broadcastMessage(msg);
    }

    private boolean giveRandomGift(boolean announceLuck) {
        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        if (players.length < minOnline) return false;
        if (itemPool.isEmpty()) return false;

        Player target = players[ThreadLocalRandom.current().nextInt(players.length)];

        Material mat = itemPool.get(rng.nextInt(itemPool.size()));
        int amount = rng.nextInt(minAmount, maxAmount + 1);

        ItemStack stack = new ItemStack(mat, amount);

        boolean hasSpace = target.getInventory().firstEmpty() != -1;
        if (hasSpace) {
            target.getInventory().addItem(stack);
        } else {
            if (fullInventoryMode == FullInventoryMode.SKIP) {
                String skip = color(getConfig().getString("messages.to-player-skip",
                        "&cИнвентарь полон, подарок пропущен."));
                target.sendMessage(skip);
                return true;
            }
            World w = target.getWorld();
            w.dropItemNaturally(target.getLocation(), stack);

            String dropMsg = color(getConfig().getString("messages.to-player-drop",
                    "&aТебе выпал подарок, но инвентарь полон — предмет выпал рядом: &e{item} x{amount}"));
            target.sendMessage(dropMsg
                    .replace("{item}", mat.name())
                    .replace("{amount}", Integer.toString(amount)));
        }

        String toPlayer = getConfig().getString("messages.to-player");
        if (toPlayer != null && !toPlayer.isEmpty()) {
            target.sendMessage(color(toPlayer)
                    .replace("{item}", mat.name())
                    .replace("{amount}", Integer.toString(amount)));
        }

        if (announceLuck) {
            String msgLuck = color(getConfig().getString(
                    "messages.luck",
                    "&6[Подарок] &eВ этот раз удача — {player}&e! &7Выпало: &e{item} x{amount}&7. Следующая удача через &e{next}&7 минут."
            ));

            Bukkit.broadcastMessage(
                    msgLuck
                            .replace("{player}", target.getName())
                            .replace("{item}", mat.name())
                            .replace("{amount}", Integer.toString(amount))
                            .replace("{next}", Long.toString(intervalMinutes))
            );
        }

        return true;
    }

    private void reloadAll() {
        reloadConfig();

        intervalMinutes = getConfig().getLong("interval-minutes", 30);
        if (intervalMinutes < 1) intervalMinutes = 1;
        intervalTicks = intervalMinutes * 60L * 20L;

        minOnline = Math.max(1, getConfig().getInt("min-online", 3));

        minAmount = Math.max(1, getConfig().getInt("amount.min", 1));
        maxAmount = Math.max(minAmount, getConfig().getInt("amount.max", 3));

        warningEnabled = getConfig().getBoolean("warning.enabled", true);
        warningMinutesBefore = Math.max(0, getConfig().getInt("warning.minutes-before", 5));

        fullInventoryMode = parseFullInventory(getConfig().getString("full-inventory", "DROP"));

        itemPool.clear();
        for (String name : getConfig().getStringList("items.whitelist")) {
            if (name == null) continue;
            Material m = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (m != null && m.isItem() && m != Material.AIR) {
                itemPool.add(m);
            }
        }
        if (itemPool.isEmpty()) {
            itemPool.add(Material.DIAMOND);
        }
    }

    private Material parseMaterial(String input) {
        if (input == null) return null;
        Material m = Material.matchMaterial(input.trim().toUpperCase(Locale.ROOT));
        if (m == null) return null;
        if (!m.isItem() || m == Material.AIR) return null;
        return m;
    }

    private String joinPool(int limit) {
        int n = itemPool.size();
        if (n == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n && i < limit; i++) {
            if (i > 0) sb.append(", ");
            sb.append(itemPool.get(i).name());
        }
        if (n > limit) sb.append(" ... +").append(n - limit);
        return sb.toString();
    }

    private String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }

    private FullInventoryMode parseFullInventory(String s) {
        if (s == null) return FullInventoryMode.DROP;
        try {
            return FullInventoryMode.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FullInventoryMode.DROP;
        }
    }
}

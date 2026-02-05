package me.yourname.randomgift;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomGiftPlugin extends JavaPlugin {

    private final ThreadLocalRandom rng = ThreadLocalRandom.current();
    private List<Material> itemPool;
    private long intervalTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        schedule();
    }

    private void loadConfig() {
        intervalTicks = getConfig().getLong("interval-minutes", 30) * 60L * 20L;

        itemPool = new ArrayList<>();
        for (String name : getConfig().getStringList("items.whitelist")) {
            Material m = Material.matchMaterial(name);
            if (m != null && m.isItem()) {
                itemPool.add(m);
            }
        }
        if (itemPool.isEmpty()) {
            itemPool.add(Material.DIAMOND);
        }
    }

    private void schedule() {
        Bukkit.getScheduler().runTaskTimer(this, this::giveRandomGift, intervalTicks, intervalTicks);
    }

    private void giveRandomGift() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        Player player = Bukkit.getOnlinePlayers().stream().findAny().orElse(null);
        if (player == null) return;

        Material mat = itemPool.get(rng.nextInt(itemPool.size()));
        ItemStack stack = new ItemStack(mat, 1);

        player.getInventory().addItem(stack);
        long minutes = getConfig().getLong("interval-minutes", 30);

        String msg = getConfig().getString("messages.luck")
                .replace("&", "§")
                .replace("{player}", player.getName())
                .replace("{item}", mat.name())
                .replace("{amount}", "1")
                .replace("{next}", String.valueOf(minutes));

        Bukkit.broadcastMessage(msg);
    }
}
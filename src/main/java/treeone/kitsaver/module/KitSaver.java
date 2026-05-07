package treeone.kitsaver.module;

import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.ArrayDeque;
import com.google.gson.Gson;
import treeone.kitsaver.Addon;
import java.lang.reflect.Type;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import com.google.common.reflect.TypeToken;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.screen.PlayerScreenHandler;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;

public class KitSaver extends Module {
    public KitSaver() {
        super(Addon.CATEGORY, "Kit Saver", "Adds buttons to Save and Load positions of inventory items.");
    }

    public static final String KITS_FILE = "meteor-client/kit.json";
    private static final String KIT_NAME = "latest";

    private final Setting<Boolean> chatNotify = settings.getDefaultGroup().add(
            new BoolSetting.Builder()
                    .name("chat-notify")
                    .description("Notify in chat when kit is saved or loaded.")
                    .defaultValue(true)
                    .build()
    );

    private final Setting<Integer> tickRate = settings.getDefaultGroup().add(
            new IntSetting.Builder()
                    .name("tick-rate")
                    .description("Ticks between each item move.")
                    .range(1, 50)
                    .sliderRange(1, 20)
                    .defaultValue(3)
                    .build()
    );

    private int ticks = 0;
    public boolean isSorted = true;
    private boolean doubleTap = false;
    private final ArrayDeque<int[]> jobs = new ArrayDeque<>();
    private final HashMap<String, HashMap<Integer, Item>> kits = new HashMap<>();

    @Override
    public void onActivate() {
        loadKitsFromFile();
    }

    @Override
    public void onDeactivate() {
        ticks = 0;
        jobs.clear();
        isSorted = true;
        doubleTap = false;
        saveKitsToFile();
    }

    public void saveKit() {
        if (mc.player == null) return;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) return;

        HashMap<Integer, Item> kit = new HashMap<>();
        for (int n = PlayerScreenHandler.EQUIPMENT_START; n < handler.slots.size(); n++) {
            ItemStack stack = handler.getSlot(n).getStack();
            if (!stack.isEmpty() && !stack.isOf(Items.AIR)) {
                kit.put(n, stack.getItem());
            }
        }
        kits.put(KIT_NAME, kit);
        saveKitsToFile();

        if (chatNotify.get()) {
            info("Kit saved.");
        }
    }

    public void loadKit() {
        if (mc.player == null) return;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) return;

        if (kits.isEmpty() || !kits.containsKey(KIT_NAME) || kits.get(KIT_NAME).isEmpty()) {
            warning("No kit found.");
            return;
        }

        jobs.clear();
        ArrayList<Integer> sorted = new ArrayList<>();
        HashMap<Integer, Item> kit = kits.get(KIT_NAME);
        HashMap<Integer, ItemStack> changedSlots = new HashMap<>();

        for (int to = PlayerScreenHandler.EQUIPMENT_START; to < handler.slots.size(); to++) {
            Item assigned = kit.get(to);
            if (assigned == null) continue;

            ItemStack current = handler.getSlot(to).getStack();

            if (current.isOf(assigned)) {
                sorted.add(to);
                continue;
            }

            for (int from = PlayerScreenHandler.EQUIPMENT_START; from < handler.slots.size(); from++) {
                if (to == from || sorted.contains(from)) continue;

                ItemStack occupiedBy;
                if (changedSlots.containsKey(from)) {
                    occupiedBy = changedSlots.get(from);
                } else {
                    occupiedBy = handler.getSlot(from).getStack();
                }

                if (occupiedBy.isOf(assigned)) {
                    if (kit.get(from) != null && occupiedBy.isOf(kit.get(from))) {
                        sorted.add(from);
                        continue;
                    }

                    if (!current.isEmpty()) {
                        sorted.add(to);
                        changedSlots.put(from, current);
                        jobs.addLast(new int[]{from, to});
                    } else {
                        sorted.add(to);
                        sorted.add(from);
                        changedSlots.remove(from);
                        jobs.addLast(new int[]{from, to});
                    }
                    break;
                }
            }
        }
    }

    private void loadKitsFromFile() {
        File file = new File(KITS_FILE);
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean ignored = parent.mkdirs();
            }
            try {
                boolean ignored = file.createNewFile();
            } catch (IOException e) {
                Addon.LOG.error("Failed to create kit file: {}", e.getMessage());
            }
            return;
        }

        Gson gson = new Gson();
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<HashMap<String, HashMap<Integer, String>>>() {}.getType();
            HashMap<String, HashMap<Integer, String>> loaded = gson.fromJson(reader, type);
            if (loaded == null) return;

            kits.clear();
            for (Map.Entry<String, HashMap<Integer, String>> entry : loaded.entrySet()) {
                HashMap<Integer, Item> itemMap = new HashMap<>();
                for (Map.Entry<Integer, String> itemId : entry.getValue().entrySet()) {
                    itemMap.put(itemId.getKey(), Registries.ITEM.get(Identifier.of(itemId.getValue())));
                }
                kits.put(entry.getKey(), itemMap);
            }
            Addon.LOG.info("Loaded kit from file.");
        } catch (Exception e) {
            Addon.LOG.error("Failed to load kit: {}", e.getMessage());
        }
    }

    private void saveKitsToFile() {
        File file = new File(KITS_FILE);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            boolean ignored = parent.mkdirs();
        }

        Gson gson = new Gson();
        try (Writer writer = new FileWriter(file)) {
            HashMap<String, HashMap<Integer, String>> nameMap = new HashMap<>();
            for (Map.Entry<String, HashMap<Integer, Item>> entry : kits.entrySet()) {
                HashMap<Integer, String> nm = new HashMap<>();
                for (Map.Entry<Integer, Item> ie : entry.getValue().entrySet()) {
                    nm.put(ie.getKey(), Registries.ITEM.getId(ie.getValue()).toString());
                }
                nameMap.put(entry.getKey(), nm);
            }
            gson.toJson(nameMap, writer);
            Addon.LOG.info("Saved kit to file.");
        } catch (Exception e) {
            Addon.LOG.error("Failed to save kit: {}", e.getMessage());
        }
    }

    private boolean isLoaded() {
        if (kits.isEmpty()) return true;
        if (mc.player == null) return true;
        if (!kits.containsKey(KIT_NAME)) return true;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler handler)) return true;

        HashMap<Integer, Item> kit = kits.get(KIT_NAME);
        for (int n = PlayerScreenHandler.EQUIPMENT_START; n < handler.slots.size(); n++) {
            if (!kit.containsKey(n)) continue;
            ItemStack stack = handler.getSlot(n).getStack();
            if (!stack.isOf(kit.get(n))) return false;
        }
        return true;
    }

    @EventHandler
    @SuppressWarnings("unused")
    void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (!(mc.player.currentScreenHandler instanceof PlayerScreenHandler)) return;

        ++ticks;
        if (ticks >= tickRate.get()) {
            ticks = 0;
            if (!jobs.isEmpty()) {
                isSorted = false;
                int[] entry = jobs.removeFirst();
                InvUtils.move().fromId(entry[0]).toId(entry[1]);
            }
            if (jobs.isEmpty() && !isSorted) {
                if (!doubleTap && !isLoaded()) {
                    doubleTap = true;
                    loadKit();
                } else {
                    isSorted = true;
                    doubleTap = false;
                }

                if (isSorted && chatNotify.get()) {
                    info("Kit loaded.");
                }
            }
        }
    }
}
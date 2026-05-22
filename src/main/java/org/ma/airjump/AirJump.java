package org.ma.airjump;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class AirJump implements ClientModInitializer {

    private static boolean was_right_clicking = false;
    private static boolean was_jumping = false;
    private static final boolean is_send_packet = true;
    private static int launch_ticks = 0;

    private static final Config CONFIG = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void onInitializeClient() {
        CONFIG.load(Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("airjump.json"));
    }

    public static void client_tick(Minecraft client) {
        if (client.player == null) return;

        boolean is_right_clicking = client.options.keyUse.isDown();
        boolean is_jumping = client.options.keyJump.isDown();
        boolean is_sneaking = client.options.keyShift.isDown();

        var hit = client.hitResult;

        boolean is_air_use = is_right_clicking
            && !client.player.isUsingItem()
            && client.screen == null
            && (
                hit == null ||
                hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
            );
        
        if (launch_ticks <= 8) {
            launch_ticks++;
            client.particleEngine.createParticle(
                ParticleTypes.EXPLOSION,
                client.player.getX(),
                client.player.getY() + 0.4D,
                client.player.getZ(),
                (Math.random() - 0.5D) * 0.4D,
                Math.random() * 0.2D,
                (Math.random() - 0.5D) * 0.4D
            );
        }

        if (CONFIG.multi_jump) {
            if (is_jumping && !was_jumping) {
                client.player.jumpFromGround();
            }

            if (client.player.fallDistance > 0 && is_sneaking) {
                Vec3 velocity = client.player.getDeltaMovement();
                client.player.setDeltaMovement(velocity.x, 0, velocity.z);
            }
        }

        if (CONFIG.launch) {
            if (is_air_use && !was_right_clicking) {
                Item launchWand = CONFIG.launchWandItem();
                if (client.player.getMainHandItem().is(launchWand) ||
                        client.player.getOffhandItem().is(launchWand)) {
                    if (is_sneaking) {
                        client.gui.getChat().addClientSystemMessage(Component.literal("-- SuperLaunch isn't allowed during sneak!").withStyle(ChatFormatting.RED));
                        was_jumping = is_jumping;
                        was_right_clicking = true;
                        return;
                    }
                    Vec3 dir = client.player.getViewVector(1.0F);
                    Vec3 boosted = dir.scale(CONFIG.launchVelocity);

                    double y = client.player.getDeltaMovement().y;
                    double b_y = boosted.y;
                    if (b_y < 0) b_y = y;

                    client.player.setDeltaMovement(boosted.x, b_y, boosted.z);
                    client.level.playLocalSound(
                        client.player.getX(),
                        client.player.getY(),
                        client.player.getZ(),
                        SoundEvents.GENERIC_EXPLODE.value(),
                        SoundSource.PLAYERS,
                        0.9F,
                        1.2F,
                        false
                    );
                    launch_ticks = 0;
                }
            }
        }

        if (CONFIG.no_fall_damage) {
            boolean is_elytra = client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
            boolean is_mace = client.player.getMainHandItem().is(Items.MACE);
            if (!client.player.isCreative() && is_send_packet && client.player.fallDistance > 2.5f && !is_elytra && !is_mace) {
                client.player.connection.send(
                        new ServerboundMovePlayerPacket.StatusOnly(true, client.player.horizontalCollision)
                );
            }
        }

        was_jumping = is_jumping;
        was_right_clicking = is_air_use;
    }

    public static Screen createSettingsScreen(Screen parent) {
        return new AirJumpSettingsScreen(parent);
    }

    private static class Config {
        private static final double DEFAULT_LAUNCH_VELOCITY = 5.0D;
        private transient Path path;
        public boolean multi_jump = true;
        public boolean launch = true;
        public boolean no_fall_damage = true;
        public String launchWand = "minecraft:stick";
        public double launchVelocity = DEFAULT_LAUNCH_VELOCITY;

        private void load(Path path) {
            this.path = path;
            if (!Files.exists(path)) {
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(path)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                if (loaded != null) {
                    multi_jump = loaded.multi_jump;
                    launch = loaded.launch;
                    no_fall_damage = loaded.no_fall_damage;
                    launchWand = loaded.launchWand == null || loaded.launchWand.isBlank() ? "minecraft:stick" : loaded.launchWand;
                    launchVelocity = sanitizeVelocity(loaded.launchVelocity);
                }
            } catch (IOException | RuntimeException ignored) {
                save();
            }
        }

        private void save() {
            if (path == null) return;

            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path)) {
                    GSON.toJson(this, writer);
                }
            } catch (IOException ignored) {
            }
        }

        private Item launchWandItem() {
            Identifier id = Identifier.tryParse(launchWand);
            if (id == null) return Items.STICK;
            Item item = BuiltInRegistries.ITEM.getValue(id);
            return item == null ? Items.STICK : item;
        }

        private boolean isValidLaunchWand() {
            Identifier id = Identifier.tryParse(launchWand);
            return id != null && BuiltInRegistries.ITEM.getOptional(id).isPresent();
        }

        private static double sanitizeVelocity(double value) {
            if (!Double.isFinite(value)) return DEFAULT_LAUNCH_VELOCITY;
            if (value <= 0.0D) return DEFAULT_LAUNCH_VELOCITY;
            return Math.max(0.1D, Math.min(20.0D, value));
        }
    }

    private static class AirJumpSettingsScreen extends Screen {
        private final Screen parent;
        private EditBox wandInput;
        private EditBox velocityInput;

        private AirJumpSettingsScreen(Screen parent) {
            super(Component.literal("AirJump Settings"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int centerX = width / 2;
            int y = height / 2 - 76;

            addRenderableWidget(Button.builder(toggleLabel("AirJump", CONFIG.multi_jump), button -> {
                CONFIG.multi_jump = !CONFIG.multi_jump;
                CONFIG.save();
                button.setMessage(toggleLabel("AirJump", CONFIG.multi_jump));
            }).bounds(centerX - 100, y, 200, 20).build());

            addRenderableWidget(Button.builder(toggleLabel("SuperLaunch", CONFIG.launch), button -> {
                CONFIG.launch = !CONFIG.launch;
                CONFIG.save();
                button.setMessage(toggleLabel("SuperLaunch", CONFIG.launch));
            }).bounds(centerX - 100, y + 24, 200, 20).build());

            addRenderableWidget(Button.builder(toggleLabel("NoFall", CONFIG.no_fall_damage), button -> {
                CONFIG.no_fall_damage = !CONFIG.no_fall_damage;
                CONFIG.save();
                button.setMessage(toggleLabel("NoFall", CONFIG.no_fall_damage));
            }).bounds(centerX - 100, y + 48, 200, 20).build());

            wandInput = addRenderableWidget(new EditBox(font, centerX - 100, y + 88, 200, 20, Component.literal("Launch wand item")));
            wandInput.setMaxLength(128);
            wandInput.setValue(CONFIG.launchWand);
            wandInput.setResponder(value -> CONFIG.launchWand = value.trim());

            velocityInput = addRenderableWidget(new EditBox(font, centerX - 100, y + 136, 200, 20, Component.literal("Launch velocity")));
            velocityInput.setMaxLength(6);
            velocityInput.setValue(String.valueOf(CONFIG.launchVelocity));
            velocityInput.setResponder(value -> {
                try {
                    CONFIG.launchVelocity = Config.sanitizeVelocity(Double.parseDouble(value.trim()));
                } catch (NumberFormatException ignored) {
                }
            });

            addRenderableWidget(Button.builder(Component.literal("Use Held Item"), button -> {
                if (minecraft == null || minecraft.player == null) return;
                ItemStack stack = minecraft.player.getMainHandItem();
                if (stack.isEmpty()) stack = minecraft.player.getOffhandItem();
                if (stack.isEmpty()) return;

                CONFIG.launchWand = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                wandInput.setValue(CONFIG.launchWand);
                CONFIG.save();
            }).bounds(centerX - 100, y + 168, 98, 20).build());

            addRenderableWidget(Button.builder(Component.literal("Save & Exit"), button -> {
                if (!CONFIG.isValidLaunchWand()) {
                    CONFIG.launchWand = "minecraft:stick";
                    wandInput.setValue(CONFIG.launchWand);
                }
                try {
                    CONFIG.launchVelocity = Config.sanitizeVelocity(Double.parseDouble(velocityInput.getValue().trim()));
                } catch (NumberFormatException ignored) {
                    CONFIG.launchVelocity = Config.DEFAULT_LAUNCH_VELOCITY;
                }
                velocityInput.setValue(String.valueOf(CONFIG.launchVelocity));
                CONFIG.save();
                onClose();
            }).bounds(centerX + 2, y + 168, 98, 20).build());
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
            super.extractRenderState(graphics, mouseX, mouseY, tickDelta);
            int centerX = width / 2;
            int y = height / 2 - 76;
            graphics.centeredText(font, title, centerX, y - 24, 0xFFFFFF);
            graphics.text(font, Component.literal("Launch wand item id"), centerX - 100, y + 76, 0xA0A0A0);
            graphics.text(font, Component.literal("Launch velocity"), centerX - 100, y + 124, 0xA0A0A0);
            graphics.text(font, wandStatus(), centerX - 100, y + 192, CONFIG.isValidLaunchWand() ? 0x80FF80 : 0xFF8080);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void onClose() {
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        }

        private static Component toggleLabel(String name, boolean enabled) {
            return Component.literal(name + ": " + (enabled ? "Enabled" : "Disabled"));
        }

        private static Component wandStatus() {
            return CONFIG.isValidLaunchWand()
                    ? Component.literal("Wand: " + CONFIG.launchWand)
                    : Component.literal("Invalid item id, save resets to minecraft:stick");
        }
    }
}

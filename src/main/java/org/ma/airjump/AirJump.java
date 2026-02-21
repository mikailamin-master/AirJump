package org.ma.airjump;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class AirJump implements ClientModInitializer {

    private boolean was_right_clicking = false;
    private boolean was_jumping = false;
    private final boolean is_send_packet = true;

    // --- settings ---
    private static class Config {
        public static boolean multi_jump = true;
        public static boolean launch = true;
        public static boolean no_fall_damage = true;
    }

    // keybinds
    private static KeyBinding key_multi_jump;
    private static KeyBinding key_launch;
    private static KeyBinding key_no_fall;

    @Override
    public void onInitializeClient() {

        key_multi_jump = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.airjump.toggle",               // translation key
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                KeyBinding.Category.MISC          // fixed category
        ));

        key_launch = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.airjump.superlaunch",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.MISC
        ));

        key_no_fall = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.airjump.nofall",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                KeyBinding.Category.MISC
        ));

        // key toggle handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (key_multi_jump.wasPressed()) {
                Config.multi_jump = !Config.multi_jump;
                client.inGameHud.getChatHud().addMessage(Text.of("-- AirJump: " + (Config.multi_jump ? "Activated" : "Deactivated")));
            }

            while (key_launch.wasPressed()) {
                Config.launch = !Config.launch;
                client.inGameHud.getChatHud().addMessage(Text.of("-- SuperLaunch: " + (Config.launch ? "Activated" : "Deactivated")));
            }

            while (key_no_fall.wasPressed()) {
                Config.no_fall_damage = !Config.no_fall_damage;
                client.inGameHud.getChatHud().addMessage(Text.of("-- NoFall: " + (Config.no_fall_damage ? "Activated" : "Deactivated")));
            }
        });

        // main movement logic
        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (client.player == null) return;

            boolean is_right_clicking = client.options.useKey.isPressed();
            boolean is_jumping = client.options.jumpKey.isPressed();
            boolean is_sneaking = client.options.sneakKey.isPressed();

            if (Config.multi_jump) {
                if (is_jumping && !was_jumping) {
                    client.player.jump();
                }

                if (client.player.fallDistance > 0 && is_sneaking) {
                    client.player.setVelocity(client.player.getVelocity().x, 0, client.player.getVelocity().z);
                }
            }

            if (Config.launch) {
                if (is_right_clicking && !was_right_clicking) {
                    if (client.player.getMainHandStack().getItem() == Items.STICK ||
                            client.player.getOffHandStack().getItem() == Items.STICK) {
                        if (is_sneaking) {
                            client.inGameHud.getChatHud().addMessage(Text.literal("-- SuperLaunch isn't allowed during sneak!").formatted(Formatting.RED));
                            was_jumping = is_jumping;
                            was_right_clicking = true;
                            return;
                        }
                        Vec3d dir = client.player.getRotationVector();
                        Vec3d boosted = dir.multiply(5);

                        double y = client.player.getVelocity().y;
                        double b_y = boosted.y;
                        if (b_y < 0) b_y = y;

                        client.player.setVelocity(boosted.x, b_y, boosted.z);
                    }
                }
            }

            was_jumping = is_jumping;
            was_right_clicking = is_right_clicking;
        });

        // no fall damage
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (!Config.no_fall_damage) return;

            boolean is_elytra = client.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
            boolean is_mace = client.player.getMainHandStack().getItem() == Items.MACE;
            if (client.player.isInCreativeMode()) return;

            if (is_send_packet && client.player.fallDistance > 2.5f && !is_elytra && !is_mace) {
                client.player.networkHandler.sendPacket(
                        new PlayerMoveC2SPacket.OnGroundOnly(true, client.player.horizontalCollision)
                );
            }
        });
    }
}

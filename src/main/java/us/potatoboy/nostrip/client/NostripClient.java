package us.potatoboy.nostrip.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

import java.io.File;


@Environment(EnvType.CLIENT)
public class NostripClient implements ClientModInitializer {
    private static KeyMapping keyBinding;
    private boolean doStrip = false;
    private final Component on = Component.translatable("text.nostrip.on");
    private final Component off = Component.translatable("text.nostrip.off");
    private static long lastMessage = 0;
    private static final int MESSAGE_REPEAT_TIME = 1000;
    private static NoStripConfig config;

    @Override
    public void onInitializeClient() {

        // Create config object from JSON
        config = NoStripConfig.loadConfig(new File(FabricLoader.getInstance().getConfigDir() + "/nostrip_config.json"));
        doStrip = config.isStripping();

        UseBlockCallback.EVENT.register(((playerEntity, world, hand, blockHitResult) -> {
            if (!world.isClientSide()) return InteractionResult.PASS;
            if (doStrip) return InteractionResult.PASS;

            ItemStack stack = playerEntity.getItemInHand(hand);
            BlockPos blockPos = blockHitResult.getBlockPos();
            BlockState blockState = world.getBlockState(blockPos);

            if (stack.getComponents().has(DataComponents.TOOL)) {
                if (AxeItem.STRIPPABLES.containsKey(blockState.getBlock())
                        || WeatheringCopper.PREVIOUS_BY_BLOCK.get().containsKey(blockState.getBlock())
                        || HoneycombItem.WAX_OFF_BY_BLOCK.get().containsKey(blockState.getBlock())
                ) {
                    informPlayer(playerEntity);
                    return InteractionResult.FAIL;
                }
            }

            if (stack.getItem() instanceof ShovelItem) {
                if (ShovelItem.FLATTENABLES.containsKey(blockState.getBlock())) {
                    informPlayer(playerEntity);
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        }));
        ClientLifecycleEvents.CLIENT_STOPPING.register((Minecraft client) -> {
            config.saveConfig(new File(FabricLoader.getInstance().getConfigDir() + "/nostrip_config.json"));
        });

        var category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("nostrip","keys")
        );

        keyBinding = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.nostrip.togglestrip",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyBinding.consumeClick()) {
                doStrip = !doStrip;

                if (client.player != null) {
                    client.player.sendOverlayMessage(
                            Component.translatable(
                                    "text.nostrip.toggle",
                                    doStrip ? on : off
                            )
                    );
                }
            }
        });
    }

    private void informPlayer(Player player) {
        if (!config.isFeedback() || System.currentTimeMillis() < lastMessage + MESSAGE_REPEAT_TIME) {
            return;
        }
        lastMessage = System.currentTimeMillis();
        Component message;
        if (KeyMappingHelper.getBoundKeyOf(keyBinding).getValue() == GLFW.GLFW_KEY_UNKNOWN) {
            message = Component.translatable("text.nostrip.prevented");
        } else {
            message = Component.translatable("text.nostrip.enableby", KeyMappingHelper.getBoundKeyOf(keyBinding).getDisplayName());
        }
        player.sendOverlayMessage(message);
    }
}

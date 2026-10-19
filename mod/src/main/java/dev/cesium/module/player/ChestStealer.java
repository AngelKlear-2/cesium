package dev.cesium.module.player;

import dev.cesium.module.Category;
import dev.cesium.module.Module;
import dev.cesium.setting.NumberSetting;
import dev.cesium.util.Mc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

/**
 * While a container screen is open, quick-moves items from the container into the player's
 * inventory, one per delay tick. Only container slots are touched; player inventory slots are skipped.
 */
public final class ChestStealer extends Module {
	private final NumberSetting delayTicks = add(new NumberSetting("Delay (ticks)", 1, 0, 10, 1));

	private int cooldown;

	public ChestStealer() {
		super("ChestStealer", "Quick-moves container items into your inventory", Category.PLAYER);
	}

	@Override
	public void onTick() {
		if (!Mc.inGame()) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		Minecraft mc = Mc.mc();
		if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		AbstractContainerMenu menu = screen.getMenu();
		int playerStart = menu.slots.size() - 36;
		for (int i = 0; i < playerStart; i++) {
			Slot s = menu.getSlot(i);
			if (s.hasItem()) {
				mc.gameMode.handleContainerInput(menu.containerId, i, 0, ClickType.QUICK_MOVE, Mc.player());
				cooldown = delayTicks.get().intValue();
				return;
			}
		}
	}
}

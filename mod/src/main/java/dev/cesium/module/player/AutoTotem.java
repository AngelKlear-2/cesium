package dev.cesium.module.player;

import dev.cesium.module.Category;
import dev.cesium.module.Module;
import dev.cesium.setting.NumberSetting;
import dev.cesium.util.Mc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

/**
 * Keeps a Totem of Undying in the offhand. When the offhand is empty or not a totem, it swaps
 * one in from the main inventory. Only acts while a container screen is closed, so it never
 * fights the player's own inventory clicks.
 */
public final class AutoTotem extends Module {
	private static final int OFFHAND_BUTTON = 40;
	private static final int OFFHAND_MENU_SLOT = 45;

	private final NumberSetting delayTicks = add(new NumberSetting("Delay (ticks)", 2, 0, 20, 1));
	private int cooldown;

	public AutoTotem() {
		super("AutoTotem", "Keeps a totem in the offhand", Category.PLAYER);
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
		if (mc.screen != null) {
			return;
		}
		LocalPlayer p = Mc.player();
		if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return;
		}

		int source = -1;
		for (int i = 0; i < 36; i++) {
			if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
				source = i;
				break;
			}
		}
		if (source < 0) {
			return;
		}

		// Inventory index -> container menu slot: hotbar 0-8 lives at 36-44, main inventory 9-35 stays put.
		int menuSlot = source < 9 ? source + 36 : source;
		mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, menuSlot, OFFHAND_BUTTON,
				ClickType.SWAP, p);
		cooldown = delayTicks.get().intValue();
	}
}

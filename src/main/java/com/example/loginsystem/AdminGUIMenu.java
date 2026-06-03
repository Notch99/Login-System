package com.example.loginsystem;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class AdminGUIMenu extends AbstractContainerMenu {
    private final Container inventory;
    private final LoginSystem loginSystem;
    private final String menuType;
    private UUID selectedPlayerUUID = null;

    public AdminGUIMenu(int syncId, Inventory playerInventory, Container inventory, LoginSystem loginSystem, String menuType) {
        super(inventory.getContainerSize() == 27 ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6, syncId);
        this.inventory = inventory;
        this.loginSystem = loginSystem;
        this.menuType = menuType;

        int rows = inventory.getContainerSize() / 9;

        // Add slots for the GUI inventory
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(Player playerEntity) {
                        return false;
                    }
                });
            }
        }

        // Add slots for player inventory
        int yOffset = (rows * 18) + 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, yOffset + row * 18));
            }
        }

        // Add slots for hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, yOffset + 58));
        }
    }

    @Override
    public void clicked(int slotIndex, int button, net.minecraft.world.inventory.ContainerInput actionType, Player player) {
        if (slotIndex < 0 || slotIndex >= inventory.getContainerSize()) {
            return;
        }

        ItemStack clickedItem = inventory.getItem(slotIndex);
        if (clickedItem.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // Bypassing NBT completely: Match strictly by Item Type and String Identifiers
        String rawName = clickedItem.getHoverName().getString().replaceAll("§[0-9a-fk-or]", "");
        
        // Main Menu: Info Book
        if (clickedItem.getItem() == Items.WRITABLE_BOOK && rawName.contains("Info")) {
            serverPlayer.closeContainer();
            loginSystem.openPlayersListGUI(serverPlayer);
            return;
        }

        // Main Menu: Delete Players
        if (clickedItem.getItem() == Items.BARRIER && rawName.contains("Delete Player")) {
            serverPlayer.closeContainer();
            loginSystem.openDeletePlayersGUI(serverPlayer);
            return;
        }
        
        // Menu item for generic lists (Player Head)
        if (clickedItem.getItem() == Items.PLAYER_HEAD) {
            // Reconstruct the exact UUID by correlating the formatted name
            UUID targetUUID = null;
            for (UUID uuid : loginSystem.getPlayerPasswords().keySet()) {
                String candidateName = loginSystem.getPlayerName(LoginSystem.serverInstance, uuid);
                if (rawName.equals(candidateName)) {
                    targetUUID = uuid;
                    break;
                }
            }
            
            if (targetUUID != null) {
                if (actionType == ContainerInput.PICKUP) {
                    if ("DELETE".equals(menuType)) {
                        deletePlayer(serverPlayer, targetUUID);
                        serverPlayer.closeContainer();
                        loginSystem.openDeletePlayersGUI(serverPlayer);
                    } else if ("VIEW".equals(menuType)) {
                        String newPassword = String.valueOf(100000 + new java.util.Random().nextInt(900000));
                        loginSystem.forceChangePassword(targetUUID, newPassword);
                        serverPlayer.sendSystemMessage(Component.literal("§aSuccessfully generated new password!").withStyle(ChatFormatting.GREEN));
                        serverPlayer.sendSystemMessage(Component.literal("§6New Password: §e" + newPassword).withStyle(ChatFormatting.GOLD));
                    }
                }
            }
        }
    }

    private void deletePlayer(ServerPlayer admin, UUID targetUUID) {
        try {
            String playerName = loginSystem.getPlayerName(LoginSystem.serverInstance, targetUUID);

            if (loginSystem.isEnableDatabase()) {
                try (Connection conn = DriverManager.getConnection(loginSystem.getJdbcUrl())) {
                    String sql = "DELETE FROM player_passwords WHERE uuid = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, targetUUID.toString());
                        int rowsAffected = pstmt.executeUpdate();
                        if (rowsAffected > 0) {
                            loginSystem.removePlayerPassword(targetUUID);
                            admin.sendSystemMessage(Component.literal("Deleted password for player: " + playerName)
                                    .withStyle(ChatFormatting.GREEN));
                        } else {
                            admin.sendSystemMessage(Component.literal("No password found for player: " + playerName)
                                    .withStyle(ChatFormatting.RED));
                        }
                    }
                }
            } else {
                if (loginSystem.hasPlayerPassword(targetUUID)) {
                    loginSystem.removePlayerPassword(targetUUID);
                    loginSystem.savePasswordsToFile();
                    admin.sendSystemMessage(Component.literal("Deleted password for player: " + playerName)
                            .withStyle(ChatFormatting.GREEN));
                } else {
                    admin.sendSystemMessage(Component.literal("No password found for player: " + playerName)
                            .withStyle(ChatFormatting.RED));
                }
            }
        } catch (SQLException e) {
            admin.sendSystemMessage(Component.literal("Failed to delete password from database!")
                    .withStyle(ChatFormatting.RED));
            LoginSystem.LOGGER.error("Failed to delete password for UUID: " + targetUUID, e);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // Disable shift-click
    }

    @Override
    public boolean stillValid(Player player) {
        return loginSystem.isPlayerOp(player); // Require admin permissions via ops.json
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        selectedPlayerUUID = null;
    }

    // Deprecated getItemNbt safely removed completely.
}

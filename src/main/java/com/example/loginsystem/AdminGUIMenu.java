package com.example.loginsystem;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public class AdminGUIMenu extends ScreenHandler {
    private final Inventory inventory;
    private final LoginSystem loginSystem;
    private final String menuType;
    private UUID selectedPlayerUUID = null;

    public AdminGUIMenu(int syncId, PlayerInventory playerInventory, Inventory inventory, LoginSystem loginSystem, String menuType) {
        super(inventory.size() == 27 ? ScreenHandlerType.GENERIC_9X3 : ScreenHandlerType.GENERIC_9X6, syncId);
        this.inventory = inventory;
        this.loginSystem = loginSystem;
        this.menuType = menuType;

        int rows = inventory.size() / 9;

        // Add slots for the GUI inventory
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean canInsert(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean canTakeItems(PlayerEntity playerEntity) {
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
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (slotIndex < 0 || slotIndex >= inventory.size()) {
            return;
        }

        ItemStack clickedItem = inventory.getStack(slotIndex);
        if (clickedItem.isEmpty() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }

        // Bypassing NBT completely: Match strictly by Item Type and String Identifiers
        String rawName = clickedItem.getName().getString().replaceAll("§[0-9a-fk-or]", "");
        
        // Main Menu: Info Book
        if (clickedItem.getItem() == Items.WRITABLE_BOOK && rawName.contains("Info")) {
            serverPlayer.closeHandledScreen();
            loginSystem.openPlayersListGUI(serverPlayer);
            return;
        }

        // Main Menu: Delete Players
        if (clickedItem.getItem() == Items.BARRIER && rawName.contains("Delete Player")) {
            serverPlayer.closeHandledScreen();
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
                if (actionType == SlotActionType.PICKUP) {
                    if ("DELETE".equals(menuType)) {
                        deletePlayer(serverPlayer, targetUUID);
                        serverPlayer.closeHandledScreen();
                        loginSystem.openDeletePlayersGUI(serverPlayer);
                    } else if ("VIEW".equals(menuType)) {
                        String newPassword = String.valueOf(100000 + new java.util.Random().nextInt(900000));
                        loginSystem.forceChangePassword(targetUUID, newPassword);
                        serverPlayer.sendMessage(Text.literal("§aSuccessfully generated new password!").formatted(Formatting.GREEN), false);
                        serverPlayer.sendMessage(Text.literal("§6New Password: §e" + newPassword).formatted(Formatting.GOLD), false);
                    }
                }
            }
        }
    }

    private void deletePlayer(ServerPlayerEntity admin, UUID targetUUID) {
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
                            admin.sendMessage(Text.literal("Deleted password for player: " + playerName)
                                    .formatted(Formatting.GREEN), false);
                        } else {
                            admin.sendMessage(Text.literal("No password found for player: " + playerName)
                                    .formatted(Formatting.RED), false);
                        }
                    }
                }
            } else {
                if (loginSystem.hasPlayerPassword(targetUUID)) {
                    loginSystem.removePlayerPassword(targetUUID);
                    loginSystem.savePasswordsToFile();
                    admin.sendMessage(Text.literal("Deleted password for player: " + playerName)
                            .formatted(Formatting.GREEN), false);
                } else {
                    admin.sendMessage(Text.literal("No password found for player: " + playerName)
                            .formatted(Formatting.RED), false);
                }
            }
        } catch (SQLException e) {
            admin.sendMessage(Text.literal("Failed to delete password from database!")
                    .formatted(Formatting.RED), false);
            LoginSystem.LOGGER.error("Failed to delete password for UUID: " + targetUUID, e);
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY; // Disable shift-click
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return loginSystem.isPlayerOp(player); // Require admin permissions via ops.json
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        selectedPlayerUUID = null;
    }

    // Deprecated getItemNbt safely removed completely.
}

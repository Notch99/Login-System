package com.example.loginsystem;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nonnull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

@SuppressWarnings("null")
public class AdminGUIMenu extends AbstractContainerMenu {
    private final Container container;
    private final LoginSystem loginSystem;

    public AdminGUIMenu(int id, Inventory playerInventory, Container container, LoginSystem loginSystem) {
        super(container.getContainerSize() == 27 ? MenuType.GENERIC_9x3 : MenuType.GENERIC_9x6, id);
        this.container = container;
        this.loginSystem = loginSystem;

        int containerRows = container.getContainerSize() / 9;

        // Add slots for the GUI
        for (int row = 0; row < containerRows; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(container, col + row * 9, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(@Nonnull ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(@Nonnull Player player) {
                        return false;
                    }
                });
            }
        }

        // Add slots for the player inventory
        int yOffset = (containerRows * 18) + 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, yOffset + row * 18));
            }
        }

        // Add slots for the hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, yOffset + 58));
        }
    }

    @Override
    public void clicked(int slotId, int button, @Nonnull ClickType clickType, @Nonnull Player player) {
        if (slotId < 0 || slotId >= container.getContainerSize()) {
            return;
        }

        ItemStack clickedItem = container.getItem(slotId);
        if (clickedItem.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        CustomData customData = clickedItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        if (customData.isEmpty()) {
            return;
        }

        CompoundTag tag = customData.copyTag();

        // Check the action type
        String action = NBTHelper.getString(tag, "GUIAction");

        if ("ViewPlayers".equals(action)) {
            // Open player list for viewing
            serverPlayer.closeContainer();
            loginSystem.openPlayersListGUI(serverPlayer);
            return;
        }

        if ("DeletePlayers".equals(action)) {
            // Open player list for deletion
            serverPlayer.closeContainer();
            loginSystem.openDeletePlayersGUI(serverPlayer);
            return;
        }

        if ("DeleteThisPlayer".equals(action) && NBTHelper.contains(tag, "PlayerUUID")) {
            // Delete player directly
            UUID targetUUID = UUID.fromString(NBTHelper.getString(tag, "PlayerUUID"));
            deletePlayer(serverPlayer, targetUUID);
            serverPlayer.closeContainer();
            loginSystem.openDeletePlayersGUI(serverPlayer);
            return;
        }

        // If a player head is clicked in the view list, generate a random password and send it to the admin
        if (clickedItem.getItem() == Items.PLAYER_HEAD && NBTHelper.contains(tag, "PlayerUUID")) {
            UUID targetUUID = UUID.fromString(NBTHelper.getString(tag, "PlayerUUID"));
            String newPassword = String.valueOf(100000 + new java.util.Random().nextInt(900000));
            loginSystem.forceChangePassword(targetUUID, newPassword);
            serverPlayer.sendSystemMessage(Component.literal("§aSuccessfully generated new password!")
                .withStyle(ChatFormatting.GREEN));
            serverPlayer.sendSystemMessage(Component.literal("§6New Password: §e" + newPassword)
                .withStyle(ChatFormatting.GOLD));
        }
    }

    private void deletePlayer(ServerPlayer admin, UUID targetUUID) {
        try {
            String playerName = loginSystem.getPlayerName(net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(), targetUUID);

            if (loginSystem.isEnableDatabase()) {
                try (Connection conn = DriverManager.getConnection(loginSystem.getJdbcUrl())) {
                    String sql = "DELETE FROM player_passwords WHERE uuid = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, targetUUID.toString());
                        int rowsAffected = pstmt.executeUpdate();
                        if (rowsAffected > 0) {
                            loginSystem.removePlayerPassword(targetUUID);
                            admin.sendSystemMessage(Component.literal("�aDeleted password for player: " + playerName)
                                .withStyle(ChatFormatting.GREEN));
                        } else {
                            admin.sendSystemMessage(Component.literal("�cNo password found for player: " + playerName)
                                .withStyle(ChatFormatting.RED));
                        }
                    }
                }
            } else {
                if (loginSystem.hasPlayerPassword(targetUUID)) {
                    loginSystem.removePlayerPassword(targetUUID);
                    loginSystem.savePasswordsToFile();
                    admin.sendSystemMessage(Component.literal("�aDeleted password for player: " + playerName)
                        .withStyle(ChatFormatting.GREEN));
                } else {
                    admin.sendSystemMessage(Component.literal("�cNo password found for player: " + playerName)
                        .withStyle(ChatFormatting.RED));
                }
            }
        } catch (SQLException e) {
            admin.sendSystemMessage(Component.literal("�cFailed to delete password from database!")
                .withStyle(ChatFormatting.RED));
            LoginSystem.LOGGER.error("Failed to delete password for UUID: " + targetUUID, e);
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@Nonnull Player player, int index) {
        return ItemStack.EMPTY; // EF9 shift-click
    }

    @Override
    public boolean stillValid(@Nonnull Player player) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server != null && PermissionHelper.hasPermissions(player, server, 2);
    }

    @Override
    public void removed(@Nonnull Player player) {
        super.removed(player);
    }
}

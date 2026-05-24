package com.example.loginsystem;

import java.lang.reflect.Method;
import java.util.Arrays;

public class ReflectionTest {
    public static void main(String[] args) {
        try {
            System.out.println("--- Methods of PlayerList ---");
            Class<?> playerListClass = Class.forName("net.minecraft.server.players.PlayerList");
            for (Method m : playerListClass.getMethods()) {
                if (m.getName().toLowerCase().contains("op") || m.getName().toLowerCase().contains("perm")) {
                    System.out.println(m.getName() + "(" + Arrays.toString(m.getParameterTypes()) + ")");
                }
            }
            
            System.out.println("--- Methods of ServerPlayer ---");
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            for (Method m : serverPlayerClass.getMethods()) {
                if (m.getName().toLowerCase().contains("server") || m.getName().toLowerCase().contains("perm")) {
                    System.out.println(m.getName() + "(" + Arrays.toString(m.getParameterTypes()) + ")");
                }
            }
            
            System.out.println("--- Methods of CommandSourceStack ---");
            Class<?> cssClass = Class.forName("net.minecraft.commands.CommandSourceStack");
            for (Method m : cssClass.getMethods()) {
                if (m.getName().toLowerCase().contains("perm")) {
                    System.out.println(m.getName() + "(" + Arrays.toString(m.getParameterTypes()) + ")");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

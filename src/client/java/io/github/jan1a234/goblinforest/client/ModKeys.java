package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.GoblinForest;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Tastenbelegung (in den Minecraft-Steuerungsoptionen unter „Goblin Forest" änderbar). Im Match wertet
 * {@link CommandScreen} die Tasten aus; Zauber und der Sammelpunkt warten danach auf einen Klick ins Feld.
 */
public final class ModKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(GoblinForest.id("main"));

	/** Taste und die Aktion, die sie an den Server schickt. */
	public record Binding(KeyMapping key, String action) {
	}

	public static final List<Binding> ACTIONS = new ArrayList<>();
	public static KeyMapping MENU;
	/** Kamera zum eigenen Häuptling springen lassen. */
	public static KeyMapping CENTER_CHIEFTAIN;

	private ModKeys() {
	}

	private static KeyMapping key(String name, int glfw) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.goblinforest." + name, glfw, CATEGORY));
	}

	public static void register() {
		MENU = key("menu", GLFW.GLFW_KEY_B);
		CENTER_CHIEFTAIN = key("center_chieftain", GLFW.GLFW_KEY_SPACE);
		ACTIONS.add(new Binding(key("chieftain", GLFW.GLFW_KEY_Q), "chieftain:toggle"));
		ACTIONS.add(new Binding(key("recruit_slave", GLFW.GLFW_KEY_Z), "recruit:slave"));
		ACTIONS.add(new Binding(key("recruit_warrior", GLFW.GLFW_KEY_X), "recruit:warrior"));
		ACTIONS.add(new Binding(key("recruit_archer", GLFW.GLFW_KEY_C), "recruit:archer"));
		ACTIONS.add(new Binding(key("recruit_assassin", GLFW.GLFW_KEY_V), "recruit:assassin"));
		ACTIONS.add(new Binding(key("recruit_shaman", GLFW.GLFW_KEY_U), "recruit:shaman"));
		ACTIONS.add(new Binding(key("recruit_wolf_rider", GLFW.GLFW_KEY_I), "recruit:wolfRider"));
		ACTIONS.add(new Binding(key("recruit_troll", GLFW.GLFW_KEY_O), "recruit:troll"));
		ACTIONS.add(new Binding(key("recruit_catapult", GLFW.GLFW_KEY_M), "recruit:catapult"));
		ACTIONS.add(new Binding(key("stance", GLFW.GLFW_KEY_H), "stance:next"));
		ACTIONS.add(new Binding(key("rally", GLFW.GLFW_KEY_N), "rally"));
		ACTIONS.add(new Binding(key("bloodlust", GLFW.GLFW_KEY_R), "ability:bloodlust"));
		ACTIONS.add(new Binding(key("battle_slam", GLFW.GLFW_KEY_G), "ability:battleSlam"));
		ACTIONS.add(new Binding(key("rage", GLFW.GLFW_KEY_Y), "ability:rage"));
		ACTIONS.add(new Binding(key("fireball", GLFW.GLFW_KEY_J), "cast:fireball"));
		ACTIONS.add(new Binding(key("healing", GLFW.GLFW_KEY_K), "cast:healing"));
		ACTIONS.add(new Binding(key("roots", GLFW.GLFW_KEY_UNKNOWN), "cast:roots"));
		ACTIONS.add(new Binding(key("lightning", GLFW.GLFW_KEY_UNKNOWN), "cast:lightning"));
		ACTIONS.add(new Binding(key("meteor", GLFW.GLFW_KEY_UNKNOWN), "cast:meteor"));
	}

	/** Hotbar-Tasten 1–9: Zauber 1–5, Fähigkeiten 6–8, Häuptling losschicken/zurückrufen 9. */
	static final String[] HOTBAR = {"cast:fireball", "cast:healing", "cast:roots", "cast:lightning", "cast:meteor",
			"ability:bloodlust", "ability:battleSlam", "ability:rage", "chieftain:toggle"};

	/** Anzeigename der Taste, die eine Aktion auslöst (für HUD und Menü), oder leer. */
	public static String label(String action) {
		for (Binding binding : ACTIONS) {
			if (binding.action().equals(action) && !binding.key().isUnbound()) {
				return binding.key().getTranslatedKeyMessage().getString();
			}
		}
		for (int i = 0; i < HOTBAR.length; i++) {
			if (HOTBAR[i].equals(action)) {
				return String.valueOf(i + 1);
			}
		}
		return "";
	}
}

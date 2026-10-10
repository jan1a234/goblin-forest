package io.github.jan1a234.goblinforest.client;

import io.github.jan1a234.goblinforest.net.ActionPayload;
import io.github.jan1a234.goblinforest.net.MatchStatePayload;
import io.github.jan1a234.goblinforest.spell.SpellType;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Die Steuerung der Draufsicht. Dieser unsichtbare Bildschirm liegt während des ganzen Matches offen, damit der
 * Mauszeiger frei ist: Linksklick auf ein Feld der Befehlsleiste ({@link MatchHud}) schickt Einheiten los, wählt einen
 * Zauber oder schickt den Häuptling. Ein gewählter Zauber (oder das Sammelbanner) wartet auf einen Klick ins Feld;
 * Rechtsklick oder Esc bricht ab. Linke Maustaste ziehen oder W/A/S/D schwenkt die Kamera, das Mausrad zoomt.
 * Alle Tasten aus den Steuerungsoptionen (Kategorie „Goblin Forest“) funktionieren weiterhin.
 */
public class CommandScreen extends Screen {
	/** Ab so vielen Pixeln Mausweg wird aus einem Klick ein Ziehen der Karte. */
	private static final double DRAG_THRESHOLD = 4;

	private static String armed;
	private static double mouseX = -1, mouseY = -1;

	private final List<KeyEvent> heldKeys = new ArrayList<>();
	private boolean pressedOnField;
	private boolean dragging;
	private double pressX, pressY;

	public CommandScreen() {
		super(Component.translatable("screen.goblinforest.command"));
	}

	/** Aktion, die gerade auf einen Klick ins Feld wartet ({@code cast:…} oder {@code rally}), sonst null. */
	public static String armedAction() {
		return armed;
	}

	/** Liegt der Mauszeiger über diesem Rechteck (GUI-Koordinaten)? */
	static boolean isHovered(int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	static void disarm() {
		armed = null;
	}

	/** Braucht diese Aktion einen Zielpunkt im Feld? */
	static boolean needsTarget(String action) {
		return action.startsWith("cast:") || action.equals("rally");
	}

	/** Wählt einen Zauber bzw. das Sammelbanner; der nächste Klick ins Feld löst es aus. */
	static void arm(String action) {
		armed = action;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void tick() {
		if (!CommandView.active()) {
			onClose();
			return;
		}
		updatePan();
		if (armed != null) {
			MatchStatePayload.ShopEntry entry = ClientMatchState.get().shopEntry(armed);
			// Abgelaufene Wahl (Zauber kühlt ab, Gold reicht nicht mehr) bleibt bestehen: der Server meldet den Grund beim Klick.
			if (armed.startsWith("cast:") && entry == null) {
				armed = null;
			}
			showTargetRing();
		}
	}

	@Override
	public void removed() {
		heldKeys.clear();
		CommandView.setPan(0, 0, false);
		mouseX = mouseY = -1;
		super.removed();
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Tastatur
	// ---------------------------------------------------------------------------------------------------------------

	@Override
	public boolean keyPressed(KeyEvent event) {
		Options options = minecraft.options;
		if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (armed != null) {
				armed = null;
			} else {
				minecraft.gui.setScreen(new PauseScreen(true));
			}
			return true;
		}
		if (isPanKey(event)) {
			heldKeys.removeIf(held -> held.key() == event.key());
			heldKeys.add(event);
			updatePan();
			return true;
		}
		if (zoomKey(event.key()) != 0) {
			CommandView.zoom(zoomKey(event.key()));
			return true;
		}
		if (ModKeys.MENU.matches(event)) {
			minecraft.gui.setScreen(new WarMenuScreen());
			return true;
		}
		if (ModKeys.CENTER_CHIEFTAIN.matches(event)) {
			centerOnChieftain();
			return true;
		}
		for (ModKeys.Binding binding : ModKeys.ACTIONS) {
			if (binding.key().matches(event)) {
				activate(binding.action());
				return true;
			}
		}
		for (int i = 0; i < options.keyHotbarSlots.length && i < ModKeys.HOTBAR.length; i++) {
			if (options.keyHotbarSlots[i].matches(event)) {
				activate(ModKeys.HOTBAR[i]);
				return true;
			}
		}
		if (options.keyChat.matches(event)) {
			minecraft.gui.setScreen(new ChatScreen("", false));
			return true;
		}
		if (options.keyCommand.matches(event)) {
			minecraft.gui.setScreen(new ChatScreen("/", false));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (heldKeys.removeIf(held -> held.key() == event.key())) {
			updatePan();
			return true;
		}
		return super.keyReleased(event);
	}

	private boolean isPanKey(KeyEvent event) {
		Options o = minecraft.options;
		return o.keyUp.matches(event) || o.keyDown.matches(event) || o.keyLeft.matches(event) || o.keyRight.matches(event)
				|| o.keySprint.matches(event) || switch (event.key()) {
					case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> true;
					default -> false;
				};
	}

	private boolean held(KeyMapping mapping, int arrow) {
		for (KeyEvent event : heldKeys) {
			if (mapping.matches(event) || event.key() == arrow) {
				return true;
			}
		}
		return false;
	}

	private void updatePan() {
		Options o = minecraft.options;
		int right = (held(o.keyRight, GLFW.GLFW_KEY_RIGHT) ? 1 : 0) - (held(o.keyLeft, GLFW.GLFW_KEY_LEFT) ? 1 : 0);
		int up = (held(o.keyUp, GLFW.GLFW_KEY_UP) ? 1 : 0) - (held(o.keyDown, GLFW.GLFW_KEY_DOWN) ? 1 : 0);
		CommandView.setPan(right, up, held(o.keySprint, GLFW.GLFW_KEY_UNKNOWN));
	}

	/**
	 * Zoomtasten für alle, die kein Mausrad haben: +/- auf dem Ziffernblock und neben der 0
	 * (auf deutschen Tastaturen liegen + und - dort, wo GLFW „]“ und „/“ meldet).
	 */
	private static int zoomKey(int key) {
		return switch (key) {
			case GLFW.GLFW_KEY_KP_ADD, GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_RIGHT_BRACKET -> 1;
			case GLFW.GLFW_KEY_KP_SUBTRACT, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_SLASH -> -1;
			default -> 0;
		};
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Maus
	// ---------------------------------------------------------------------------------------------------------------

	@Override
	public void mouseMoved(double x, double y) {
		mouseX = x;
		mouseY = y;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		mouseX = event.x();
		mouseY = event.y();
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			armed = null;
			return true;
		}
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		MatchHud.Hotspot spot = MatchHud.hotspotAt(event.x(), event.y());
		if (spot != null) {
			activate(spot.action());
			return true;
		}
		if (MatchHud.blocksClick(event.x(), event.y())) {
			return true;
		}
		pressedOnField = true;
		dragging = false;
		pressX = event.x();
		pressY = event.y();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		mouseX = event.x();
		mouseY = event.y();
		if (!pressedOnField || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (!dragging && Math.hypot(event.x() - pressX, event.y() - pressY) >= DRAG_THRESHOLD) {
			dragging = true;
			// Der bisherige Weg zählt mit, damit die Karte nicht hinter dem Mauszeiger zurückbleibt.
			CommandView.drag(event.x() - pressX - dx, event.y() - pressY - dy, height);
		}
		if (dragging) {
			CommandView.drag(dx, dy, height);
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean wasField = pressedOnField;
		pressedOnField = false;
		if (wasField && !dragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			clickField(event.x(), event.y());
		}
		dragging = false;
		return wasField || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY != 0) {
			CommandView.zoom(scrollY);
		}
		return true;
	}

	/** Klick ins Feld: ein gewählter Zauber oder das Sammelbanner landet dort. */
	private void clickField(double x, double y) {
		if (armed == null) {
			return;
		}
		Vec3 point = CommandView.groundPoint(x, y, width, height);
		if (point == null) {
			return;
		}
		send(armed + "@" + CommandView.point(point.x, point.z));
		armed = null;
	}

	/** Auslösen einer Aktion von Leiste oder Taste. */
	void activate(String action) {
		switch (action) {
			case "menu" -> minecraft.gui.setScreen(new WarMenuScreen());
			case "center" -> centerOnChieftain();
			default -> {
				if (needsTarget(action)) {
					// Nochmal dieselbe Taste = abbrechen.
					armed = action.equals(armed) ? null : action;
				} else {
					send(action);
				}
			}
		}
	}

	private void centerOnChieftain() {
		MatchStatePayload s = ClientMatchState.get();
		if (s.chieftainDead()) {
			return;
		}
		CommandView.jumpTo(s.chieftainX(), s.chieftainZ());
	}

	private static void send(String action) {
		ClientPlayNetworking.send(new ActionPayload(action));
	}

	/** Zielkreis am Boden unter dem Mauszeiger, so groß wie die Wirkung des Zaubers. */
	private void showTargetRing() {
		if (minecraft.level == null || mouseX < 0 || MatchHud.blocksClick(mouseX, mouseY)) {
			return;
		}
		Vec3 point = CommandView.groundPoint(mouseX, mouseY, width, height);
		if (point == null) {
			return;
		}
		double radius = 1.5;
		int color = 0xF2C744;
		if (armed.startsWith("cast:")) {
			SpellType spell = SpellType.byId(armed.substring(5));
			radius = spell == null ? 3 : ClientMatchState.get().spellRadius(spell.ordinal());
			color = spell != null && !spell.offensive() ? 0x6BE06B : 0xFF5030;
		}
		DustParticleOptions dust = new DustParticleOptions(color, 1.3f);
		int points = Math.max(12, (int) (radius * 6));
		for (int i = 0; i < points; i++) {
			double angle = Math.PI * 2 * i / points;
			minecraft.level.addParticle(dust, point.x + Math.cos(angle) * radius, point.y + 0.15, point.z + Math.sin(angle) * radius, 0, 0, 0);
		}
	}

	// ---------------------------------------------------------------------------------------------------------------
	// Zeichnen
	// ---------------------------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		// Durchsichtig: man sieht das Schlachtfeld und das HUD darunter.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
		super.extractRenderState(g, mx, my, partialTick);
		mouseX = mx;
		mouseY = my;
		MatchHud.Hotspot spot = MatchHud.hotspotAt(mx, my);
		if (spot != null && !pressedOnField) {
			List<FormattedCharSequence> lines = new ArrayList<>();
			for (Component line : spot.tooltip()) {
				lines.addAll(font.split(line, 220));
			}
			g.setTooltipForNextFrame(font, lines, mx, my);
		} else if (armed != null) {
			Component name = armed.startsWith("cast:") && SpellType.byId(armed.substring(5)) != null
					? Component.translatable(SpellType.byId(armed.substring(5)).translationKey())
					: Component.translatable("hud.goblinforest.slot.rally");
			Component hint = Component.translatable("hud.goblinforest.target", name).withStyle(ChatFormatting.GOLD);
			Component cancel = Component.translatable("hud.goblinforest.target.cancel").withStyle(ChatFormatting.GRAY);
			int x = Math.min(mx + 10, width - Math.max(font.width(hint), font.width(cancel)) - 2);
			int y = Math.max(2, my - 22);
			g.text(font, hint, x, y, 0xFFFFFFFF, true);
			g.text(font, cancel, x, y + 10, 0xFFFFFFFF, true);
		}
	}
}

/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package net.montoyo.wd.client.gui.controls;

import net.minecraft.client.gui.GuiButton;
import net.montoyo.wd.client.gui.loading.JsonOWrapper;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class Button extends Control {

    private final GuiButton btn;
    private boolean selected = false;
    private boolean shiftDown = false;
    private int originalColor = 0;
    private int shiftColor = 0;

    public static class ClickEvent extends Event<Button> {

        private boolean shiftDown;

        private ClickEvent(Button btn) {
            source = btn;
            shiftDown = btn.shiftDown;
        }

        public boolean isShiftDown() {
            return shiftDown;
        }

    }

    public Button() {
        btn = new GuiButton(0, 0, 0, "");
    }

    public Button(String text, int x, int y, int width) {
        btn = new GuiButton(0, x, y, width, 20, text);
    }

    public Button(String text, int x, int y) {
        btn = new GuiButton(0, x, y, text);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if(mouseButton == 0 && btn.mousePressed(mc, mouseX, mouseY)) {
            selected = true;
            btn.playPressSound(mc.getSoundHandler());
            parent.actionPerformed(new ClickEvent(this));
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        if(selected && state == 0) {
            btn.mouseReleased(mouseX, mouseY);
            selected = false;
        }
    }

    @Override
    public void draw(int mouseX, int mouseY, float ptt) {
        btn.drawButton(mc, mouseX, mouseY, ptt);
    }

    public void setLabel(String label) {
        btn.displayString = label;
    }

    public String getLabel() {
        return btn.displayString;
    }

    public void setWidth(int width) {
        btn.setWidth(width);
    }

    @Override
    public int getWidth() {
        return btn.getButtonWidth();
    }

    @Override
    public int getHeight() {
        return 20;
    }

    @Override
    public void setPos(int x, int y) {
        btn.x = x;
        btn.y = y;
    }

    @Override
    public int getX() {
        return btn.x;
    }

    @Override
    public int getY() {
        return btn.y;
    }

    public GuiButton getMcButton() {
        return btn;
    }

    public void setDisabled(boolean dis) {
        btn.enabled = !dis;
    }

    public boolean isDisabled() {
        return !btn.enabled;
    }

    public void enable() {
        btn.enabled = true;
    }

    public void disable() {
        btn.enabled = false;
    }

    public void setVisible(boolean visible) {
        btn.visible = visible;
    }

    public boolean isVisible() {
        return btn.visible;
    }

    public void show() {
        btn.visible = true;
    }

    public void hide() {
        btn.visible = false;
    }

    public boolean isShiftDown() {
        return shiftDown;
    }

    @Override
    public void keyUp(int key) {
        if(key == Keyboard.KEY_LSHIFT || key == Keyboard.KEY_RSHIFT) {
            shiftDown = false;
            btn.packedFGColour = originalColor;
        }
    }

    @Override
    public void keyDown(int key) {
        if(key == Keyboard.KEY_LSHIFT || key == Keyboard.KEY_RSHIFT) {
            shiftDown = true;
            btn.packedFGColour = shiftColor;
        }
    }

    public void setTextColor(int color) {
        originalColor = color;
        if(!shiftDown)
            btn.packedFGColour = color;
    }

    public int getTextColor() {
        return btn.packedFGColour;
    }

    public void setShiftTextColor(int shiftColor) {
        this.shiftColor = shiftColor;
        if(shiftDown)
            btn.packedFGColour = shiftColor;
    }

    public int getShiftTextColor() {
        return shiftColor;
    }

    @Override
    public void load(JsonOWrapper json) {
        super.load(json);
        btn.x = json.getInt("x", 0);
        btn.y = json.getInt("y", 0);
        btn.width = json.getInt("width", 200);
        btn.displayString = tr(json.getString("label", ""));
        btn.enabled = !json.getBool("disabled", false);
        btn.visible = json.getBool("visible", true);

        originalColor = json.getColor("color", 0);
        shiftColor = json.getColor("shiftColor", 0);
        btn.packedFGColour = originalColor;
    }

}
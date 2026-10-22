package org.telegram.messenger.family;

import org.telegram.messenger.BotInlineKeyboard;

/** Preserve native custom-button dispatch while using FamilyGram bundled translations. */
public final class FamilyButton extends BotInlineKeyboard.ButtonCustom {
    private final String key;

    public FamilyButton(int id, String key, int icon) {
        super(id, 0, icon);
        this.key = key;
    }

    @Override
    public String getText() {
        return FamilyText.get(key);
    }
}

package com.example.message.models;

public class LanguageItem {
    public String code;
    public String name;
    public String nativeName;
    public int flagResId;
    public boolean selected;

    public LanguageItem(String code, String name, String nativeName, int flagResId, boolean selected) {
        this.code = code;
        this.name = name;
        this.nativeName = nativeName;
        this.flagResId = flagResId;
        this.selected = selected;
    }

    public LanguageItem(String code, String name, String nativeName, int flagResId) {
        this(code, name, nativeName, flagResId, false);
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getNativeName() { return nativeName; }
    public int getFlagResId() { return flagResId; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}

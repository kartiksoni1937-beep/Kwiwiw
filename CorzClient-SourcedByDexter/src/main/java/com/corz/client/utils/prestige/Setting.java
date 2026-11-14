package com.corz.client.utils.prestige;

import java.util.function.Predicate;

public abstract class Setting {
    private String name;
    private Object value;
    private String description;
    private Predicate visibility;

    public Setting(String settingName, Object defaultValue) {
        this.name = settingName;
        this.value = defaultValue;
    }

    @Chainable
    public String getName() {
        return this.name;
    }

    public void setName(String newName) {
        this.name = newName;
    }

    @Chainable
    public Object getValue() {
        return this.value;
    }

    @Chainable
    public void setValue(Object newValue) {
        this.value = newValue;
    }

    @Chainable
    public String getDescription() {
        return this.description;
    }

    @Chainable
    public Setting setDescription(String newDescription) {
        this.description = newDescription;
        return this;
    }

    public Setting setVisibility(Predicate visibilityPredicate) {
        this.visibility = visibilityPredicate;
        return this;
    }

    @Chainable
    public boolean isVisible() {
        return this.visibility == null || this.visibility.test(this.getValue());
    }
}

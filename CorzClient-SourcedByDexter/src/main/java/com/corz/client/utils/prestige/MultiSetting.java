package com.corz.client.utils.prestige;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class MultiSetting extends Setting {
    @Override
    public String getValue() {
        return (String)super.getValue();
    }

    private final String[] options;
    private final boolean[] selected;
    private final Map indexByOption;

    public MultiSetting(String settingName, String[] optionValues, boolean[] defaultSelection) {
        super(settingName, "");
        this.options = optionValues;
        this.selected = defaultSelection;
        this.indexByOption = new HashMap(optionValues.length * 2);

        for (int index = 0; index < optionValues.length; index++) {
            this.indexByOption.put(optionValues[index], index);
        }
    }

    @Chainable
    public String[] getOptions() {
        return this.options;
    }

    public boolean isSelected(String option) {
        Integer optionIndex = (Integer)this.indexByOption.get(option);
        if (optionIndex == null) {
            throw new IllegalArgumentException("Value not found: " + option);
        } else {
            return this.selected[optionIndex];
        }
    }

    @Chainable
    public void setSelected(String option, boolean selectedState) {
        Integer optionIndex = (Integer)this.indexByOption.get(option);
        if (optionIndex == null) {
            throw new IllegalArgumentException("Value not found: " + option);
        } else {
            this.selected[optionIndex] = selectedState;
        }
    }

    @Chainable
    public boolean[] getSelected() {
        return this.selected;
    }

    public MultiSetting visibleWhen(Predicate visibilityPredicate) {
        super.setVisibility(visibilityPredicate);
        return this;
    }

    @Chainable
    public MultiSetting withDescription(String newDescription) {
        super.setDescription(newDescription);
        return this;
    }

    @Override
    public Setting setVisibility(Predicate visibilityPredicate) {
        return this.visibleWhen(visibilityPredicate);
    }

    @Chainable
    @Override
    public Setting setDescription(String newDescription) {
        return this.withDescription(newDescription);
    }
}

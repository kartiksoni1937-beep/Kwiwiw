package com.corz.client.config;

import com.corz.client.utils.MinmaxEnumType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiSelectSetting extends ConfigSetting {
   private final List<String> options;
   private final Set<String> defaults;
   private final Set<String> selected;

   public MultiSelectSetting(String settingName, String description, String[] options, boolean[] defaultSelection) {
      super(settingName, description);
      this.options = new ArrayList<>(Arrays.asList(options));
      this.defaults = new LinkedHashSet<>();
      this.selected = new LinkedHashSet<>();

      for (int index = 0; index < this.options.size(); index++) {
         if (defaultSelection != null && index < defaultSelection.length && defaultSelection[index]) {
            this.defaults.add(this.options.get(index));
         }
      }

      this.selected.addAll(this.defaults);
   }

   public List<String> getOptions() {
      return this.options;
   }

   public List<String> getSelected() {
      return new ArrayList<>(this.selected);
   }

   public boolean isSelected(String option) {
      return option != null && this.selected.contains(option);
   }

   public void setSelected(String option, boolean selectedState) {
      if (option == null || !this.options.contains(option)) {
         return;
      }

      boolean changed = selectedState ? this.selected.add(option) : this.selected.remove(option);
      if (changed) {
         ModuleConfigAutosave.updateFlag2();
      }
   }

   public void toggle(String option) {
      if (option != null && this.options.contains(option)) {
         if (!this.selected.remove(option)) {
            this.selected.add(option);
         }
         ModuleConfigAutosave.updateFlag2();
      }
   }

   public void reset() {
      this.selected.clear();
      this.selected.addAll(this.defaults);
      ModuleConfigAutosave.updateFlag2();
   }

   @Override
   public JsonElement getJsonValue() {
      JsonObject object = new JsonObject();
      for (String option : this.options) {
         object.addProperty(option, this.selected.contains(option));
      }
      return object;
   }

   @Override
   public ConfigSetting.Applied applyJsonValue(JsonElement json) {
      if (json == null) {
         return ConfigSetting.Applied.NOT_HANDLED;
      }

      Set<String> next = new LinkedHashSet<>();
      boolean handled = false;

      if (json.isJsonObject()) {
         for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            if (this.options.contains(entry.getKey()) && entry.getValue().isJsonPrimitive()) {
               try {
                  if (entry.getValue().getAsBoolean()) {
                     next.add(entry.getKey());
                  }
                  handled = true;
               } catch (Exception ignored) {
               }
            }
         }
      } else if (json.isJsonArray()) {
         JsonArray array = json.getAsJsonArray();
         for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
               String option = element.getAsString();
               if (this.options.contains(option)) {
                  next.add(option);
               }
            }
         }
         handled = true;
      }

      if (!handled) {
         return ConfigSetting.Applied.NOT_HANDLED;
      }

      this.selected.clear();
      this.selected.addAll(next);
      ModuleConfigAutosave.updateFlag2();
      return ConfigSetting.Applied.APPLIED;
   }

   @Override
   public MinmaxEnumType getSettingType() {
      return MinmaxEnumType.MULTI_SELECT;
   }
}

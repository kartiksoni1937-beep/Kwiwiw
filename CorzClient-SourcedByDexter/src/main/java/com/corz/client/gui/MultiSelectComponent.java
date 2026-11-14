package com.corz.client.gui;

import com.corz.client.CorzClient;
import com.corz.client.config.MultiSelectSetting;
import com.corz.client.render.IcoGenUtils;
import com.corz.client.render.MatrixEspUtils;
import com.corz.client.render.MatrixPeekUtilsIdentifier;
import com.corz.client.render.VertexTextureFont;
import java.awt.Color;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

public class MultiSelectComponent {
   private final MultiSelectSetting setting;
   private int leftX;
   private int topY;
   private int width;
   private int height;

   public MultiSelectComponent(MultiSelectSetting setting, int x, int y, int width, int height) {
      this.setting = setting;
      this.leftX = x;
      this.topY = y;
      this.width = width;
      this.height = height;
   }

   public static int getHeight(MultiSelectSetting setting) {
      int rows = (setting.getOptions().size() + 1) / 2;
      return 20 + rows * 18;
   }

   public void handle(int x, int y, int width, int height) {
      this.leftX = x;
      this.topY = y;
      this.width = width;
      this.height = height;
   }

   private int getColumnWidth() {
      return Math.max(1, (this.width - 8) / 2);
   }

   private int getBoxX(int index) {
      int column = index % 2;
      return this.leftX + 4 + column * this.getColumnWidth();
   }

   private int getRowY(int index) {
      return this.topY + 20 + index / 2 * 18;
   }

   private boolean isHovered(double mouseX, double mouseY, int index) {
      int boxX = this.getBoxX(index);
      int rowY = this.getRowY(index);
      int columnWidth = this.getColumnWidth();
      return mouseX >= boxX - 2
         && mouseX <= boxX + columnWidth - 4
         && mouseY >= rowY
         && mouseY <= rowY + 17;
   }

   public boolean canFlag(double mouseX, double mouseY, int button) {
      if (button != 0) {
         return false;
      }

      List<String> options = this.setting.getOptions();
      for (int index = 0; index < options.size(); index++) {
         if (this.isHovered(mouseX, mouseY, index)) {
            this.setting.toggle(options.get(index));
            return true;
         }
      }

      return false;
   }

   public void render(DrawContext context, int mouseX, int mouseY) {
      VertexTextureFont fontRenderer = CorzClient.fontRenderer();
      fontRenderer.handleForStringFloat(
         this.setting.getSettingName(),
         this.leftX + 4,
         fontRenderer.getAbcdefghijklmnopqrstuvwxyzBrown(this.topY, 18),
         new Color(-3947581)
      );

      List<String> options = this.setting.getOptions();
      int columnWidth = this.getColumnWidth();
      int textHeight = Math.max(7, (int)fontRenderer.getAbcdefghijklmnopqrstuvwxyzBrown());

      for (int index = 0; index < options.size(); index++) {
         String option = options.get(index);
         int boxX = this.getBoxX(index);
         int rowY = this.getRowY(index);
         boolean hovered = this.isHovered(mouseX, mouseY, index);
         boolean selected = this.setting.isSelected(option);

         if (hovered) {
            int hoverColor = MatrixEspUtils.releaseRightValue & 0x22FFFFFF;
            MatrixPeekUtilsRender.render(context, boxX - 2, rowY, columnWidth - 2, 17, 3, hoverColor, hoverColor);
         }

         int backgroundColor = selected
            ? MatrixEspUtils.releaseRightValue
            : (hovered ? -14012614 : -15329252);
         int borderColor = selected ? -1 : (hovered ? -11117467 : -16448249);
         MatrixPeekUtilsRender.render(context, boxX, rowY + 4, 9, 9, 2, backgroundColor, borderColor);

         if (selected) {
            MatrixStack matrixStack = MatrixPeekUtilsRender.getMatrixStack();
            MatrixPeekUtilsIdentifier.handle(
               boxX + 1.0F,
               rowY + 5.0F,
               7.0F,
               7.0F,
               IcoGenUtils.getIcoCheck(),
               new Color(255, 255, 255, 245),
               matrixStack
            );
         }

         int textColor = selected ? -920587 : -7630437;
         fontRenderer.handleForStringFloat(
            option,
            boxX + 13,
            rowY + (18 - textHeight) / 2.0F,
            new Color(textColor)
         );
      }
   }

   public int getComponentHeight() {
      return this.height;
   }
}

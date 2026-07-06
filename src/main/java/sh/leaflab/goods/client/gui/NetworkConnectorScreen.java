package sh.leaflab.goods.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import sh.leaflab.goods.menu.NetworkConnectorMenu;
import sh.leaflab.goods.network.SetNetworkNamePayload;

public class NetworkConnectorScreen extends AbstractContainerScreen<NetworkConnectorMenu> {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 76;

    private EditBox nameField;
    private Button doneButton;

    public NetworkConnectorScreen(NetworkConnectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GUI_WIDTH, GUI_HEIGHT);
        this.inventoryLabelY = 10000;
        this.titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();

        int cx = leftPos + imageWidth / 2;

        this.nameField = new EditBox(this.font, cx - 70, topPos + 24, 140, 18, Component.literal("Network Name"));
        this.nameField.setMaxLength(64);
        this.nameField.setResponder(text -> {
            this.doneButton.active = !text.trim().isEmpty();
        });
        this.addWidget(this.nameField);

        this.doneButton = this.addRenderableWidget(Button.builder(
                Component.translatable("gui.thegoods.network_connector.done"),
                btn -> sendAndClose()
        ).bounds(cx - 30, topPos + 50, 60, 20).build());
        this.doneButton.active = false;

        this.setInitialFocus(this.nameField);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == 257 || event.key() == 335) && doneButton.active) {
            sendAndClose();
            return true;
        }
        if (this.nameField.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    private void sendAndClose() {
        String name = nameField.getValue().trim();
        ClientPacketDistributor.sendToServer(new SetNetworkNamePayload(name));
        this.onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF555555);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, 0xFF555555);
        graphics.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
        graphics.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF555555);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }
}

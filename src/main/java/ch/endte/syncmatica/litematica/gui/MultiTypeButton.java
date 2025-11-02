package ch.endte.syncmatica.litematica.gui;

import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;

import java.util.List;

public class MultiTypeButton extends ButtonGeneric {

    IButtonType activeType;
    List<IButtonType> types;

    public MultiTypeButton(final int x, final int y, final boolean rightAligned, final List<IButtonType> types) {
        super(x, y, 1, 20, "");
        this.types = types;
        activeType = types.get(0);
        update();
        width = calculateWidth();
        if (rightAligned) {
            this.x -= width;
        }
        actionListener = new MultiTypeListener();
    }

    public void update() {
        updateType();
        displayString = activeType.getTranslatedKey();
        if (activeType.getHoverStrings() != null) {
            final List<String> hoverStrings = activeType.getHoverStrings();
            setHoverStrings(hoverStrings.toArray(new String[0]));
        }
        setEnabled(activeType.getButtonListener() != null);
    }

    private void updateType() {
        for (final IButtonType type : types) {
            if (type.isActive()) {
                activeType = type;
                return;
            }
        }

        activeType = types.get(0);
    }

    public IButtonType getActiveType() {
        return activeType;
    }

    private int calculateWidth() {
        int wMax = -1;
        for (final IButtonType type : types) {
            final int wType = calculateWidth(type);
            if (wType > wMax) {
                wMax = wType;
            }
        }
        return wMax;
    }

    private int calculateWidth(final IButtonType type) {
        int w = 0;
        if (type.getTranslatedKey() != null) {

            w += getStringWidth(type.getTranslatedKey()) + 10;
        }
        if (type.getIcon() != null) {
            w += type.getIcon().getWidth() + 8;
        }
        return w;
    }

    @Override
    public MultiTypeButton setActionListener(final IButtonActionListener listener) {
        return this;
    }

    public class MultiTypeListener implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(final ButtonBase button, final int mouseButton) {
            update();
            if (activeType.getButtonListener() != null) {
                activeType.getButtonListener().actionPerformedWithButton(button, mouseButton);
            }
        }

    }

}

/*
 * Copyright 2018 Laszlo Balazs-Csiki and Contributors
 *
 * This file is part of Pixelitor. Pixelitor is free software: you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License, version 3 as published by the Free
 * Software Foundation.
 *
 * Pixelitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Pixelitor. If not, see <http://www.gnu.org/licenses/>.
 */

package pixelitor.tools.gui;

import pixelitor.tools.Tool;
import pixelitor.tools.Tools;
import pixelitor.utils.Icons;
import pixelitor.utils.Messages;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * The button that activates a tool
 */
public class ToolButton extends JToggleButton implements ActionListener {
    private final Tool tool;

    public ToolButton(Tool tool) {
        this.tool = tool;
        tool.setButton(this);

        // used for component lookup when testing
        String buttonName = tool.getName() + " Tool Button";
        setName(buttonName);

        putClientProperty("JComponent.sizeVariant", "mini");

        Icon icon = Icons.load(tool.getIconFileName());
        setIcon(icon);

        assert icon.getIconWidth() == 30;
        assert icon.getIconHeight() == 30;

        char c = tool.getActivationKeyChar();
        String s = new String(new char[]{c}).toUpperCase();
        setToolTipText("<html>" + tool.getName() + " Tool (<b>" + s + "</b>)");

        setMargin(new Insets(0, 0, 0, 0));
        setBorderPainted(true);
        setRolloverEnabled(false);
        addActionListener(this);

        int size = 44; // the icons are 30*30

        Dimension preferredSize = new Dimension(size, size);
        setPreferredSize(preferredSize);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Tools.changeTo(tool);

        String msg = "<html>" + tool.getName() + " Tool: " + tool.getToolMessage();
        Messages.showInStatusBar(msg);
    }

    public Tool getTool() {
        return tool;
    }
}

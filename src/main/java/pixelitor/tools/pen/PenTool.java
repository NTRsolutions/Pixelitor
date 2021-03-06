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

package pixelitor.tools.pen;

import pixelitor.Build;
import pixelitor.Canvas;
import pixelitor.Composition;
import pixelitor.gui.ImageComponent;
import pixelitor.gui.ImageComponents;
import pixelitor.gui.utils.Dialogs;
import pixelitor.history.History;
import pixelitor.history.PixelitorEdit;
import pixelitor.tools.ClipStrategy;
import pixelitor.tools.Tool;
import pixelitor.tools.Tools;
import pixelitor.tools.pen.history.ConvertPathToSelectionEdit;
import pixelitor.tools.util.PMouseEvent;
import pixelitor.utils.Cursors;
import pixelitor.utils.test.RandomGUITest;

import javax.swing.*;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;

/**
 * The Pen Tool
 */
public class PenTool extends Tool {
    private static final String MODE_BUILD = "Build";
    private static final String MODE_EDIT = "Edit";

    private JComboBox<String> modeChooser;
    private final AbstractAction toSelectionAction;

    private Path path;
    private PenToolMode mode = new PathBuilder();
    private boolean ignoreModeChooserAction = false;
    private AbstractAction dumpAction;

    public PenTool() {
        super("Pen", 'p', "pen_tool_icon.png",
                "<b>click</b> and <b>drag</b> to create a Bezier curve. " +
                        "<b>Ctrl-click</b> or close the path to finish. " +
                        "Press <b>Esc</b> to start from scratch.",
                Cursors.DEFAULT, false, true,
                ClipStrategy.INTERNAL_FRAME);
        toSelectionAction = new AbstractAction("Convert to Selection") {
            @Override
            public void actionPerformed(ActionEvent e) {
                convertToSelection(true);
            }
        };
        toSelectionAction.setEnabled(false);
    }

    private void convertToSelection(boolean addToHistory) {
        Path oldPath = path;

        Shape shape = path.toImageSpaceShape();
        Composition comp = ImageComponents.getActiveCompOrNull();

        PixelitorEdit selectionEdit = comp.setSelectionFromShapeComplete(shape);
        if (selectionEdit == null) {
            Dialogs.showInfoDialog("No Selection",
                    "No selection was created because the path is outside the canvas.");
            return;
        }

        comp.setActivePath(null);
        resetStateToInitial();
        Tools.SELECTION.activate();

        if (addToHistory) {
            History.addEdit(new ConvertPathToSelectionEdit(comp, oldPath, selectionEdit));
        }
    }

    @Override
    public void initSettingsPanel() {
        modeChooser = new JComboBox<>(new String[]{MODE_BUILD, MODE_EDIT});
        modeChooser.addActionListener(e -> onModeChooserAction());
        settingsPanel.addWithLabel("Mode:", modeChooser);

        settingsPanel.addButton(toSelectionAction,
                "Convert the active path to a selection");

        if (Build.CURRENT.isDevelopment()) {
            dumpAction = new AbstractAction("dump") {
                @Override
                public void actionPerformed(ActionEvent e) {
                    path.dump();
                }
            };
            settingsPanel.addButton(dumpAction);
        }
    }

    private void onModeChooserAction() {
        if (ignoreModeChooserAction) {
            return;
        }
        if (modeChooser.getSelectedItem().equals("Build")) {
            resetStateToInitial();
        } else {
            startEditing(true);
        }
    }

    public void startEditing(boolean pathWasBuiltInteractively) {
        if (!hasPath()) {
            EventQueue.invokeLater(() -> {
                if (!RandomGUITest.isRunning()) {
                    Dialogs.showInfoDialog("No Path", "There is no path to edit.");
                }
                modeChooser.setSelectedItem(MODE_BUILD);
            });
            return;
        }

        ignoreModeChooserAction = true;
        modeChooser.setSelectedItem(MODE_EDIT);
        ignoreModeChooserAction = false;

        path.changeTypesForEditing(pathWasBuiltInteractively);
        mode = new PathEditor(path);
        toSelectionAction.setEnabled(true);
        ImageComponents.repaintActive();
    }

    @Override
    public void resetStateToInitial() {
        ignoreModeChooserAction = true;
        modeChooser.setSelectedItem(MODE_BUILD);
        ignoreModeChooserAction = false;

        toSelectionAction.setEnabled(false);
        setPath(null);
        mode = new PathBuilder();
        ImageComponents.repaintActive();
    }

    @Override
    public void mousePressed(PMouseEvent e) {
        if (path == null) {
            assert mode instanceof PathBuilder;
            setPath(new Path(e.getComp()));
        }
        mode.mousePressed(e);
    }

    @Override
    public void mouseDragged(PMouseEvent e) {
        mode.mouseDragged(e);
        e.repaint();
    }

    @Override
    public void mouseReleased(PMouseEvent e) {
        mode.mouseReleased(e);
        e.repaint();
    }

    @Override
    public void mouseMoved(MouseEvent e, ImageComponent ic) {
        if (mode.mouseMoved(e, ic)) {
            ic.repaint();
        }
    }

    @Override
    public void paintOverImage(Graphics2D g2, Canvas canvas, ImageComponent ic,
                               AffineTransform componentTransform,
                               AffineTransform imageTransform) {
        g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);

        mode.paint(g2);
    }

    @Override
    public void coCoordsChanged(ImageComponent ic) {
        if (path != null) {
            path.coCoordsChanged(ic);
        }
    }

    @Override
    public void escPressed() {
        resetStateToInitial();
    }

    public void setPath(Path path) {
        this.path = path;
        mode.setPath(this.path);
        if (dumpAction != null) {
            dumpAction.setEnabled(hasPath());
        }
    }

    public boolean hasPath() {
        return path != null;
    }

    @Override
    protected void toolStarted() {
        super.toolStarted();

        Composition comp = ImageComponents.getActiveCompOrNull();
        if (comp != null) {
            setPath(comp.getActivePath());
            comp.repaint();
        }
    }

    @Override
    protected void toolEnded() {
        super.toolEnded();

        Composition comp = ImageComponents.getActiveCompOrNull();
        if (comp != null) {
            comp.setActivePath(path);
        }

        setPath(null);
        if (comp != null) {
            comp.repaint();
        }
    }

    public void setBuilderState(PathBuilder.State state) {
        PathBuilder pb = (PathBuilder) mode;
        pb.setState(state);
    }
}

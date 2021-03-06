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

package pixelitor.tools;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import pixelitor.Build;
import pixelitor.Composition;
import pixelitor.TestHelper;
import pixelitor.gui.GlobalKeyboardWatch;
import pixelitor.gui.ImageComponent;
import pixelitor.tools.gui.ToolSettingsPanel;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.awt.event.MouseEvent.MOUSE_DRAGGED;
import static java.awt.event.MouseEvent.MOUSE_PRESSED;
import static java.awt.event.MouseEvent.MOUSE_RELEASED;

/**
 * Behavior that is common to all tools
 */
@RunWith(Parameterized.class)
public class ToolTest {
    private ImageComponent ic;

    @Parameter
    public Tool tool;

    @Parameter(value = 1)
    public Alt alt;

    @Parameter(value = 2)
    public Ctrl ctrl;

    @Parameter(value = 3)
    public Shift shift;

    @Parameter(value = 4)
    public MouseButton mouseButton;

    @Parameters(name = "{index}: tool = {0} Tool, alt = {1}, ctrl = {2}, shift = {3}, mouseButton = {4}")
    public static Collection<Object[]> instancesToTest() throws InvocationTargetException, InterruptedException {
        Tools.CLONE.setState(CloneTool.State.CLONING);

        Tool[] tools = Tools.getAll();
//        Tool[] tools = {Tools.BRUSH};

        List<Object[]> instances = new ArrayList<>();
        for (Tool tool : tools) {
            tool.setSettingsPanel(new ToolSettingsPanel());
//            tool.initSettingsPanel();
            SwingUtilities.invokeAndWait(tool::initSettingsPanel);

            // for each combination, create an independent test run
            for (Alt alt : Alt.values()) {
                for (Ctrl ctrl : Ctrl.values()) {
                    for (Shift shift : Shift.values()) {
                        for (MouseButton mouseButton : MouseButton.values()) {
                            instances.add(new Object[]{tool, alt, ctrl, shift, mouseButton});
                        }
                    }
                }
            }
        }

        return instances;
    }

    @BeforeClass
    public static void setupClass() {
        Build.setTestingMode();
        TestHelper.setupMockFgBgSelector();
    }

    @Before
    public void setUp()  {
        tool.toolStarted();
        Composition comp = TestHelper.create2LayerComposition(true);

        ic = comp.getIC();
    }

    @After
    public void tearDown() {
        tool.toolEnded();
    }

    @Test
    public void simpleStroke() {
        stroke(alt, ctrl, shift, mouseButton);

        // also test with space down
        GlobalKeyboardWatch.setSpaceDown(true);
        stroke(alt, ctrl, shift, mouseButton);
        GlobalKeyboardWatch.setSpaceDown(false);
    }

    private void stroke(Alt alt, Ctrl ctrl, Shift shift, MouseButton mouseButton) {
        tool.handlerChain
                .handleMousePressed(TestHelper.createEvent(ic, MOUSE_PRESSED, alt, ctrl, shift, mouseButton, 2, 2));
        tool.handlerChain
                .handleMouseDragged(TestHelper.createEvent(ic, MOUSE_DRAGGED, alt, ctrl, shift, mouseButton, 4, 4));
        tool.handlerChain
                .handleMouseReleased(TestHelper.createEvent(ic, MOUSE_RELEASED, alt, ctrl, shift, mouseButton, 6, 6));
    }
}

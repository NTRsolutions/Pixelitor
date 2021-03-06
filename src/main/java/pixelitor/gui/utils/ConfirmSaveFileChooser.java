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

package pixelitor.gui.utils;

import javax.swing.*;
import java.io.File;

/**
 * A save file chooser that confirms before overwriting a file
 */
public class ConfirmSaveFileChooser extends JFileChooser {
    public ConfirmSaveFileChooser(File currentDir) {
        super(currentDir);
    }

    public ConfirmSaveFileChooser(String currentDirPath) {
        super(currentDirPath);
    }

    @Override
    public void approveSelection() {
        File f = getSelectedFile();
        if (f.exists()) {
            String msg = f.getName() + " exists already. Overwrite?";
            if (!Dialogs.showYesNoQuestionDialog(this, "Confirmation", msg)) {
                return;
            }
        }
        super.approveSelection();
    }
}

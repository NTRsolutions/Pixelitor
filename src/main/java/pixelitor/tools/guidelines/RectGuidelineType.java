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

package pixelitor.tools.guidelines;

/**
 * Crop guidelines types
 */
public enum RectGuidelineType {
    NONE("None"),
    RULE_OF_THIRDS("Rule of Thirds"),
    GOLDEN_SECTIONS("Golden Sections"),
    DIAGONALS("Diagonal Lines");

    private final String guiName;

    RectGuidelineType(String guiName) {
        this.guiName = guiName;
    }

    @Override
    public String toString() {
        return guiName;
    }
}

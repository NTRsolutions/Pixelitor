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

import pixelitor.Composition;
import pixelitor.gui.View;
import pixelitor.history.History;
import pixelitor.tools.pen.history.AddAnchorPointEdit;
import pixelitor.tools.pen.history.CloseSubPathEdit;
import pixelitor.tools.util.DraggablePoint;
import pixelitor.utils.Shapes;
import pixelitor.utils.debug.Ansi;

import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static pixelitor.tools.pen.PathBuilder.State.DRAGGING_THE_CONTROL_OF_LAST;
import static pixelitor.tools.pen.PathBuilder.State.MOVING_TO_NEXT_CURVE_POINT;

/**
 * A subpath within a {@link Path}
 * <p>
 * It is a composite Bezier curve: a series of Bezier curves
 * joined end to end where the last point of one curve
 * coincides with the starting point of the next curve.
 * <p>
 * https://en.wikipedia.org/wiki/Composite_B%C3%A9zier_curve
 */
public class SubPath implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<AnchorPoint> anchorPoints = new ArrayList<>();
    private final Composition comp;
    // The curve point which is currently moving while the path is being built
    private AnchorPoint moving;

    // The curve point which was added first
    // Relevant for closing
    private AnchorPoint first;

    // The curve point which was finalized last
    // Relevant because its handle is dragged while the path is being built
    private AnchorPoint last;

    private boolean closed = false;

    private static final ToDoubleFunction<DraggablePoint> TO_CO_X = p -> p.x;
    private static final ToDoubleFunction<DraggablePoint> TO_CO_Y = p -> p.y;
    private static final ToDoubleFunction<DraggablePoint> TO_IM_X = p -> p.imX;
    private static final ToDoubleFunction<DraggablePoint> TO_IM_Y = p -> p.imY;

    public SubPath(Composition comp) {
        this.comp = comp;
    }

    public void addFirstPoint(AnchorPoint p) {
        anchorPoints.add(p);
        p.setPath(this);
        first = p;
        last = p;
    }

    // not used in the builder, only when converting from external shape
    public void addPoint(AnchorPoint p) {
        if (p.samePositionAs(last)) {
            // strangely, shapes generated by Area operations
            // can contain SEG_LINETOs moving to the last position
            return;
        }
        anchorPoints.add(p);
        p.setPath(this);
        last = p;
    }

    public void setMoving(AnchorPoint p) {
        moving = p;
    }

    public void finalizeMovingPoint(int x, int y, boolean finishSubPath) {
        moving.setLocation(x, y);
        moving.calcImCoords();
        anchorPoints.add(moving);
        moving.setPath(this);
        last = moving;
        moving = null;

        History.addEdit(new AddAnchorPointEdit(
                comp, this, last, finishSubPath));
    }

    public AnchorPoint getMoving() {
        return moving;
    }

    public AnchorPoint getFirst() {
        return first;
    }

    public AnchorPoint getLast() {
        return last;
    }

    public int getNumPoints() {
        return anchorPoints.size();
    }

    public void addToComponentSpaceShape(GeneralPath path) {
        // TODO cache, but one must be careful to
        // re-create after any editing
        addToShape(path, TO_CO_X, TO_CO_Y);
    }

    public void addToImageSpaceShape(GeneralPath path) {
        addToShape(path, TO_IM_X, TO_IM_Y);
    }

    private void addToShape(GeneralPath gp,
                            ToDoubleFunction<DraggablePoint> toX,
                            ToDoubleFunction<DraggablePoint> toY) {

        // TODO maybe a Path should be created only when there is at least one node
        if (first == null) {
            return;
        }

        // moveTo is the beginning of a new subpath
        gp.moveTo(toX.applyAsDouble(first), toY.applyAsDouble(first));
        AnchorPoint prev = first;

        for (int i = 1; i < anchorPoints.size(); i++) {
            AnchorPoint curr = anchorPoints.get(i);
            ControlPoint prevCtrlOut = prev.ctrlOut;
            ControlPoint currCtrlIn = curr.ctrlIn;
            if (prevCtrlOut.isRetracted() && currCtrlIn.isRetracted()) {
                gp.lineTo(
                        toX.applyAsDouble(curr),
                        toY.applyAsDouble(curr)
                );
            } else {
                gp.curveTo(
                        toX.applyAsDouble(prevCtrlOut),
                        toY.applyAsDouble(prevCtrlOut),
                        toX.applyAsDouble(currCtrlIn),
                        toY.applyAsDouble(currCtrlIn),
                        toX.applyAsDouble(curr),
                        toY.applyAsDouble(curr));
            }
            prev = curr;
        }
        if (moving != null) {
            gp.curveTo(
                    toX.applyAsDouble(last.ctrlOut),
                    toY.applyAsDouble(last.ctrlOut),
                    toX.applyAsDouble(moving.ctrlIn),
                    toY.applyAsDouble(moving.ctrlIn),
                    toX.applyAsDouble(moving),
                    toY.applyAsDouble(moving));
        }

        if (closed) {
            ControlPoint lastCtrlOut = last.ctrlOut;
            ControlPoint firstCtrlIn = first.ctrlIn;
            if (lastCtrlOut.isRetracted() && firstCtrlIn.isRetracted()) {
                gp.lineTo(
                        toX.applyAsDouble(first),
                        toY.applyAsDouble(first)
                );
            } else {
                gp.curveTo(
                        toX.applyAsDouble(lastCtrlOut),
                        toY.applyAsDouble(lastCtrlOut),
                        toX.applyAsDouble(firstCtrlIn),
                        toY.applyAsDouble(firstCtrlIn),
                        toX.applyAsDouble(first),
                        toY.applyAsDouble(first));
            }
            // We reached the first point again,
            // however call this to add a clean SEG_CLOSE.
            gp.closePath();
        }
    }

    public void paintHandlesForBuilding(Graphics2D g, PathBuilder.State state) {
        int numPoints = getNumPoints();
        if (state == DRAGGING_THE_CONTROL_OF_LAST) {
            if (numPoints > 1) {
                last.paintHandles(g, true, true);
            } else {
                // special case: only one point, no shape
                if (!last.samePositionAs(last.ctrlOut)) {
                    Line2D.Double line = new Line2D.Double(
                            last.x, last.y, last.ctrlOut.x, last.ctrlOut.y);
                    Shapes.drawVisible(g, line);
                }
            }
        } else if (state == MOVING_TO_NEXT_CURVE_POINT) {
            boolean paintIn = true;
            if (numPoints <= 2) {
                paintIn = false;
            }
            last.paintHandles(g, paintIn, true);
            if (first.isActive()) {
//                first.paintHandle(g);
                first.paintHandles(g, true, false);
            }
        }
    }

    public void paintHandlesForEditing(Graphics2D g) {
        int numPoints = anchorPoints.size();
        for (int i = 0; i < numPoints; i++) {
            boolean paintIn = true;
            boolean paintOut = true;
            if (!closed && (i == 0)) {
                // don't paint the in control handle for the first point
                paintIn = false;
            }
            if (!closed && (i == numPoints - 1)) {
                // don't paint the out control handle for the last point
                paintOut = false;
            }

            AnchorPoint point = anchorPoints.get(i);
            point.paintHandles(g, paintIn, paintOut);
        }
    }

    public DraggablePoint handleWasHit(int x, int y, boolean altDown) {
        for (AnchorPoint point : anchorPoints) {
            DraggablePoint draggablePoint = point.handleOrCtrlHandleWasHit(x, y, altDown);
            if (draggablePoint != null) {
                return draggablePoint;
            }
        }
        return null;
    }

    public void close(boolean addToHistory) {
        int numPoints = anchorPoints.size();

        // this condition doesn't occur while building a path interactively,
        // only when converting from closed Shape objects
        boolean lastIsFirst = numPoints > 1 && last.samePositionAs(first);
        if (lastIsFirst) {
            assert last != first;

            // the last added point is identical to the first, so remove it
            int indexOfLast = numPoints - 1;
            anchorPoints.remove(indexOfLast);

            // copy the the useful info
            first.ctrlIn.copyPositionFrom(last.ctrlIn);

            // make sure we have a valid last reference
            last = anchorPoints.get(indexOfLast - 1);
        }
        moving = null; // can be ignored in this case
        closed = true;

        if (addToHistory) {
            History.addEdit(new CloseSubPathEdit(comp, this));
        }
    }

    public void undoClosing() {
        moving = new AnchorPoint(first, false);
        closed = false;
    }

    public boolean isClosed() {
        return closed;
    }

    public void coCoordsChanged(View view) {
        for (AnchorPoint point : anchorPoints) {
            point.restoreCoordsFromImSpace(view);
            point.ctrlIn.restoreCoordsFromImSpace(view);
            point.ctrlOut.restoreCoordsFromImSpace(view);
        }
    }

    /**
     * Checks whether all the objects are wired together correctly
     */
    @SuppressWarnings("SameReturnValue")
    public boolean checkWiring() {
        int numPoints = anchorPoints.size();
        for (int i = 0; i < numPoints; i++) {
            AnchorPoint point = anchorPoints.get(i);
            ControlPoint ctrlIn = point.ctrlIn;
            ControlPoint ctrlOut = point.ctrlOut;
            if (ctrlIn.getAnchor() != point) {
                throw new IllegalStateException("ctrlIn problem in point " + i);
            }
            if (ctrlIn.getSibling() != ctrlOut) {
                throw new IllegalStateException("ctrlIn problem in point " + i);
            }
            if (ctrlOut.getAnchor() != point) {
                throw new IllegalStateException("ctrlOut problem in point " + i);
            }
            if (ctrlOut.getSibling() != ctrlIn) {
                throw new IllegalStateException("ctrlOut problem in point " + i);
            }
            if (ctrlIn == ctrlOut) {
                throw new IllegalStateException("same controls in point " + i);
            }
        }
        return true;
    }

    public void dump() {
        int numPoints = anchorPoints.size();
        PrintStream out = System.out;
        if (numPoints == 0) {
            out.println("Empty path");
        }
        for (int i = 0; i < numPoints; i++) {
            AnchorPoint point = anchorPoints.get(i);
            out.print(Ansi.purple("Point " + i + ": "));
            if (point == first) {
                out.print("first ");
            }
            if (point == last) {
                out.print("last ");
            }
            if (point == moving) {
                out.print("moving ");
            }
            point.dump();
        }
    }

    public AnchorPoint getPoint(int index) {
        return anchorPoints.get(index);
    }

    public void changeTypesForEditing(boolean pathWasBuiltInteractively) {
        for (AnchorPoint point : anchorPoints) {
            point.changeTypeForEditing(pathWasBuiltInteractively);
        }
    }

    public void deletePoint(AnchorPoint ap) {
        boolean wasFirst = ap == first;
        boolean wasLast = ap == last;
        // don't use List.remove, because it uses equals
        // and not reference equality
        int index = -1;
        for (int i = 0; i < anchorPoints.size(); i++) {
            if (anchorPoints.get(i) == ap) {
                index = i;
                break;
            }
        }
        if (wasFirst) {
            assert index == 0;
            first = anchorPoints.get(index + 1);
        }
        if (wasLast) {
            assert index == anchorPoints.size() - 1;
            last = anchorPoints.get(index - 1);
        }
        anchorPoints.remove(index);
    }

    public void deleteLast() {
        int indexOfLast = anchorPoints.size() - 1;
        AnchorPoint removed = anchorPoints.remove(indexOfLast);
        last = anchorPoints.get(indexOfLast - 1);

        if (moving == null) { // when undoing a finished subpath
            moving = removed;
        }
    }

    public void setView(View view) {
        for (AnchorPoint ap : anchorPoints) {
            ap.setView(view);
        }
        if (moving != null) {
            moving.setView(view);
        }
    }
}
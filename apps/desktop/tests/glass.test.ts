import { test } from "node:test";
import assert from "node:assert/strict";
import {
  visibleBounds,
  magneticBounds,
  roundedShape,
  panelSettings,
} from "../shared/glass";

test("panels restore on negative-coordinate second monitors and remain fully visible after disconnection", () => {
  const displays = [
    { id: 1, workArea: { x: 0, y: 0, width: 1920, height: 1040 } },
    { id: 2, workArea: { x: -2560, y: 0, width: 2560, height: 1400 } },
  ];
  const saved = { x: -2200, y: 100, width: 360, height: 420 };
  assert.deepEqual(visibleBounds(saved, displays, 2), saved);
  const restored = visibleBounds(saved, [displays[0]], 2);
  assert(
    restored.x >= 0 &&
      restored.y >= 0 &&
      restored.x + restored.width <= 1920 &&
      restored.y + restored.height <= 1040,
  );
  const resized = visibleBounds({ x: 1800, y: 950, width: 1200, height: 900 }, [
    { id: 1, workArea: { x: 0, y: 0, width: 800, height: 560 } },
  ]);
  assert.equal(resized.width, 800);
  assert.equal(resized.height, 560);
  assert.equal(resized.x, 0);
  assert.equal(resized.y, 0);
});
test("100%, 125%, 150% and 200% display bounds use DIP coordinates without scaling positions twice", () => {
  for (const scaleFactor of [1, 1.25, 1.5, 2]) {
    const area = {
      x: 1920,
      y: 0,
      width: Math.round(2560 / scaleFactor),
      height: Math.round(1440 / scaleFactor),
    };
    const result = visibleBounds(
      { x: 3400, y: 1000, width: 500, height: 400 },
      [{ id: 7, workArea: area, scaleFactor }],
      7,
    );
    assert(result.x >= area.x);
    assert(result.y >= area.y);
    assert(result.x + result.width <= area.x + area.width);
    assert(result.y + result.height <= area.height);
  }
});
test("edge snapping and neighboring panels align only within the magnetic threshold", () => {
  const area = { x: 0, y: 0, width: 1920, height: 1040 };
  assert.equal(
    magneticBounds({ x: 6, y: 90, width: 360, height: 420 }, area, []).x,
    0,
  );
  assert.equal(
    magneticBounds({ x: 100, y: 400, width: 300, height: 200 }, area, [
      { x: 100, y: 100, width: 300, height: 290 },
    ]).y,
    398,
  );
  assert.equal(
    magneticBounds({ x: 50, y: 90, width: 360, height: 420 }, area, []).x,
    50,
  );
});
test("native rounded regions remove corner pixels while keeping content and transparency settings legible", () => {
  const shape = roundedShape(360, 330);
  const contains = (x: number, y: number) =>
    shape.some(
      (r) => x >= r.x && y >= r.y && x < r.x + r.width && y < r.y + r.height,
    );
  assert(!contains(0, 0));
  assert(!contains(359, 329));
  assert(contains(180, 0));
  assert(contains(180, 165));
  assert.equal(panelSettings({ opacity: 0.2 }).opacity, 0.7);
  assert.equal(panelSettings({ opacity: 5 }).opacity, 1);
});

import {test} from "node:test";
import assert from "node:assert/strict";
import {splitShape} from "../src/js/atr/AtrShape.mjs";

const shape = [[0, 0, 0], [1, 0, 0], [2, 0, 0], [3, 0, 0], [4, 0, 0], [5, 0, 0]];

test("a shape with no straight ranges is one rail run", () => {
    assert.deepEqual(splitShape(shape, []), [{straight: false, points: shape}]);
    assert.deepEqual(splitShape(shape, undefined), [{straight: false, points: shape}]);
});

test("straight ranges become their own runs that share end points with the rail runs", () => {
    assert.deepEqual(splitShape(shape, [[2, 3]]), [
        {straight: false, points: shape.slice(0, 3)},
        {straight: true, points: shape.slice(2, 4)},
        {straight: false, points: shape.slice(3)},
    ]);
});

test("a straight range at the start or end leaves no empty runs", () => {
    assert.deepEqual(splitShape(shape.slice(0, 2), [[0, 1]]), [{straight: true, points: shape.slice(0, 2)}]);
    assert.deepEqual(splitShape(shape, [[4, 5]]), [
        {straight: false, points: shape.slice(0, 5)},
        {straight: true, points: shape.slice(4)},
    ]);
});

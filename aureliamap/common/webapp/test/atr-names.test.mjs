import {test} from "node:test";
import assert from "node:assert/strict";
import {pickName, routeKey} from "../src/js/atr/AtrNames.mjs";

test("latin mode picks the English line, not the station code", () => {
    assert.equal(pickName("伊爾洛·布朗森|Irlo Bronson|OGL08", "latin"), "Irlo Bronson");
    assert.equal(pickName("奧蕾莉亞中央車站|Aurelia Centraal", "latin"), "Aurelia Centraal");
    assert.equal(pickName("Olympia South", "latin"), "Olympia South");
});

test("latin mode falls back to the first line when nothing is latin", () => {
    assert.equal(pickName("空港リンク", "latin"), "空港リンク");
});

test("the part after || is dropped, first and all modes", () => {
    assert.equal(pickName("空港リンク|Airport Link||Southbound", "latin"), "Airport Link");
    assert.equal(pickName("空港リンク|Airport Link||Southbound", "first"), "空港リンク");
    assert.equal(pickName("空港リンク|Airport Link||Southbound", "all"), "空港リンク / Airport Link");
});

test("empty and missing names give an empty string", () => {
    assert.equal(pickName("", "latin"), "");
    assert.equal(pickName(undefined, "latin"), "");
});

test("both directions of a line share one key", () => {
    const north = {name: "空港リンク|Airport Link||Northbound", color: "2F2F4F"};
    const south = {name: "空港リンク|Airport Link||Southbound", color: "2F2F4F"};
    const other = {name: "Mistport Line||to Olympia T1", color: "C491A8"};
    assert.equal(routeKey(north), routeKey(south));
    assert.notEqual(routeKey(north), routeKey(other));
});

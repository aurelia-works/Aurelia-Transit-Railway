import {test} from "node:test";
import assert from "node:assert/strict";
import {countdown, arrivalRows} from "../src/js/atr/AtrArrivals.mjs";

test("countdown reads Due, minutes, or Departed", () => {
    assert.equal(countdown(10 * 1000), "Due");
    assert.equal(countdown(31 * 1000), "1 min");
    assert.equal(countdown(150 * 1000), "3 min");
    assert.equal(countdown(-5 * 1000), "Due");
    assert.equal(countdown(-60 * 1000), "Departed");
});

test("rows are sorted by arrival, skip departed trains, and use the server clock", () => {
    const serverNow = 1_000_000;
    const response = {currentTime: serverNow, data: {arrivals: [
        {destination: "B|Bravo", arrival: serverNow + 300_000, departure: serverNow + 310_000, platformName: "2", routeName: "Red|Red Line||North", routeNumber: "R", routeColor: 0xFF0000, realtime: true, isTerminating: false, cars: [{}, {}]},
        {destination: "A|Alpha", arrival: serverNow + 60_000, departure: serverNow + 70_000, platformName: "1", routeName: "Blue", routeNumber: "", routeColor: 0x0000FF, realtime: false, isTerminating: false, cars: [{}]},
        {destination: "C", arrival: serverNow - 120_000, departure: serverNow - 110_000, platformName: "1", routeName: "Blue", routeNumber: "", routeColor: 0x0000FF, realtime: true, isTerminating: false, cars: []},
    ]}};
    // the browser clock is 5 s ahead of the server: countdowns must not shift
    const rows = arrivalRows(response, serverNow + 5000, serverNow + 5000, "latin");
    assert.deepEqual(rows.map(row => row.destination), ["Alpha", "Bravo"]);
    assert.equal(rows[0].countdown, "1 min");
    assert.equal(rows[0].platform, "1");
    assert.equal(rows[0].color, "#0000ff");
    assert.equal(rows[0].scheduled, true);
    assert.equal(rows[1].route, "Red Line");
    assert.equal(rows[1].cars, 2);
});

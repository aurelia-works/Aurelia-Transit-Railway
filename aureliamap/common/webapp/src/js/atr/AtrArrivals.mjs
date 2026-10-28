import {pickName} from "./AtrNames.mjs";

/**
 * @param millis {number} time until the train arrives
 * @returns {string} "Due" within 30 s (or just arrived), "Departed" a minute after, else whole minutes rounded up
 */
export function countdown(millis) {
    if (millis <= -60 * 1000) return "Departed";
    if (millis <= 30 * 1000) return "Due";
    return `${Math.ceil(millis / 60000)} min`;
}

/**
 * Turns an {@code /mtr/api/map/arrivals} response into panel rows, soonest first.
 * Countdowns use the server clock: {@code receivedAt} is the browser time when the response came in.
 *
 * @param response {{currentTime: number, data: {arrivals: object[]}}}
 * @param receivedAt {number} Date.now() when the response arrived
 * @param now {number} Date.now() at render time
 * @param nameMode {string} see pickName
 */
export function arrivalRows(response, receivedAt, now, nameMode) {
    const serverNow = response.currentTime + (now - receivedAt);
    return (response.data.arrivals || [])
        .filter(arrival => arrival.departure > serverNow)
        .sort((arrival1, arrival2) => arrival1.arrival - arrival2.arrival)
        .map(arrival => ({
            destination: pickName(arrival.destination, nameMode),
            route: pickName(arrival.routeName, nameMode),
            number: arrival.routeNumber || "",
            color: "#" + (arrival.routeColor & 0xFFFFFF).toString(16).padStart(6, "0"),
            platform: arrival.platformName || "",
            countdown: countdown(arrival.arrival - serverNow),
            scheduled: !arrival.realtime,
            terminating: !!arrival.isTerminating,
            cars: (arrival.cars || []).length,
        }));
}

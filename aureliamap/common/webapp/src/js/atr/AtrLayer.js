/*
 * AureliaMap: the ATR (MTR) layer. Draws routes along their rails, stations with labels and live trains,
 * and opens an arrivals panel when a station is clicked. Data comes from the ATR server's webserver:
 *   /atr/api/map/network   stations, routes and route shapes (ATR, cached 60 s on the server)
 *   /atr/api/map/vehicles  live vehicle positions (ATR, cached 1 s)
 *   /mtr/api/map/arrivals  arrivals at a station (MTR core)
 */
import {MarkerSet} from "../markers/MarkerSet";
import {Vector3} from "three";
import {AtrArrivalsPanel} from "./AtrArrivalsPanel";
import {escapeHtml, pickName, routeKey} from "./AtrNames.mjs";
import {splitShape} from "./AtrShape.mjs";

export const ATR_MARKER_SETS = ["atr-lines", "atr-stations", "atr-trains"];

const STORAGE_KEY = "aureliamap-atr";
const NETWORK_INTERVAL = 60 * 1000;
const VEHICLES_INTERVAL = 1000;

export const ATR_DEFAULTS = Object.freeze({
    enabled: true,
    lines: true,
    lineWidth: 4,
    lineOpacity: 0.9,
    straightLines: false,
    stations: true,
    labelDistance: 1,
    labelColor: "#ffffff",
    trains: true,
    trainSize: 1,
    trainColor: "",
    nameMode: "latin",
    apiUrl: "",
});

export class AtrLayer {

    /**
     * @param app {BlueMapApp}
     */
    constructor(app) {
        this.app = app;
        this.settings = app.appState.atr;
        Object.assign(this.settings, ATR_DEFAULTS, loadSettings());

        this.lineSet = new MarkerSet("atr-lines", {label: "ATR lines", toggleable: false, sorting: -30});
        this.stationSet = new MarkerSet("atr-stations", {label: "ATR stations", toggleable: false, sorting: -20});
        this.trainSet = new MarkerSet("atr-trains", {label: "ATR trains", toggleable: false, sorting: -10});

        this.network = null;
        this.dimensions = null;
        this.dimension = 0;
        this.trainMotion = new Map();
        this.timers = [];
        this.run = 0;
        this.panel = new AtrArrivalsPanel(this);

        this.onClick = event => {
            const element = event.target.closest && event.target.closest(".atr-station");
            if (!element) return;
            event.stopPropagation();
            this.panel.open(element.dataset.id);
        };
        document.addEventListener("click", this.onClick, true);

        this.animate = this.animate.bind(this);
        requestAnimationFrame(this.animate);
    }

    get apiUrl() {
        const fromQuery = new URLSearchParams(window.location.search).get("atr");
        const url = fromQuery || this.settings.apiUrl || `${window.location.protocol}//${window.location.hostname}:8888`;
        return url.replace(/\/+$/, "");
    }

    /**
     * Called after BlueMap switched maps.
     */
    async setMap(map) {
        this.stop();
        // a later setMap or stop while this one waits on the server cancels it, so only one set of timers runs
        const run = this.run;
        const markers = this.app.mapViewer.markers;
        ATR_MARKER_SETS.forEach(id => markers.markerSets.has(id) || markers.add(this.setById(id)));
        this.lineSet.updateMarkersFromData({});
        this.stationSet.updateMarkersFromData({});
        this.trainSet.updateMarkersFromData({});
        this.trainMotion.clear();
        this.network = null;
        this.applyVisibility();
        if (!map || !this.settings.enabled) return;

        try {
            if (!this.dimensions) {
                const response = await this.fetchJson("/mtr/api/map/stations-and-routes");
                this.dimensions = response.data.dimensions || [];
            }
            this.dimension = Math.max(0, this.dimensions.indexOf(dimensionOfMap(map.data.id)));
        } catch (e) {
            console.warn("ATR layer: no ATR server at " + this.apiUrl, e);
            return;
        }

        if (run !== this.run) return;
        await this.updateNetwork();
        await this.updateVehicles();
        if (run !== this.run) return;
        this.timers.push(setInterval(() => this.updateNetwork(), NETWORK_INTERVAL));
        this.timers.push(setInterval(() => this.updateVehicles(), VEHICLES_INTERVAL));
    }

    stop() {
        this.run++;
        this.timers.forEach(timer => clearInterval(timer));
        this.timers = [];
    }

    setById(id) {
        return id === "atr-lines" ? this.lineSet : id === "atr-stations" ? this.stationSet : this.trainSet;
    }

    fetchJson(path, body) {
        const separator = path.includes("?") ? "&" : "?";
        const url = `${this.apiUrl}${path}${separator}dimension=${this.dimension}`;
        return fetch(url, body ? {method: "POST", body: JSON.stringify(body)} : undefined).then(response => {
            if (!response.ok) throw new Error(`${url}: ${response.status}`);
            return response.json();
        });
    }

    async updateNetwork() {
        try {
            const response = await this.fetchJson("/atr/api/map/network");
            this.network = response.data;
            this.drawNetwork();
        } catch (e) {
            console.warn("ATR layer: network update failed", e);
        }
    }

    async updateVehicles() {
        if (!this.settings.enabled || !this.settings.trains) return;
        try {
            const response = await this.fetchJson("/atr/api/map/vehicles");
            this.drawVehicles(response.data.vehicles);
        } catch (e) {
            console.warn("ATR layer: vehicle update failed", e);
        }
    }

    /**
     * Re-applies every setting (called from the settings menu).
     */
    applySettings() {
        saveSettings(this.settings);
        this.applyVisibility();
        if (this.settings.enabled && this.timers.length === 0 && this.app.mapViewer.map) {
            this.setMap(this.app.mapViewer.map);
            return;
        }
        if (!this.settings.enabled) this.stop();
        this.drawNetwork();
        this.app.mapViewer.redraw();
    }

    applyVisibility() {
        this.lineSet.visible = this.settings.enabled && this.settings.lines;
        this.stationSet.visible = this.settings.enabled && this.settings.stations;
        this.trainSet.visible = this.settings.enabled && this.settings.trains;
    }

    drawNetwork() {
        if (!this.network) return;
        const settings = this.settings;
        const stationHeights = new Map();
        const lines = {};

        this.network.routes.forEach(route => {
            route.platforms.forEach(platform => {
                const heights = stationHeights.get(platform.stationId) || [];
                heights.push(platform.y);
                stationHeights.set(platform.stationId, heights);
            });
            if (route.hidden || route.shape.length < 2) return;
            const color = route.suspended ? {r: 120, g: 120, b: 120} : hexToRgb(route.color);
            const label = (route.suspended ? "Suspended: " : "") + pickName(route.name, settings.nameMode);
            // where no rails were found between two platforms the server sends a straight line, which would cut across the map
            splitShape(route.shape, route.straight).forEach((run, index) => {
                if (run.straight && !settings.straightLines) return;
                lines[`${route.id}-${index}`] = {
                    type: "line",
                    label,
                    position: {x: run.points[0][0], y: run.points[0][1], z: run.points[0][2]},
                    line: run.points.map(([x, y, z]) => ({x, y: y + 1, z})),
                    lineWidth: run.straight ? Math.max(1, settings.lineWidth / 2) : settings.lineWidth,
                    lineColor: {...color, a: run.straight ? settings.lineOpacity / 2 : settings.lineOpacity},
                    depthTest: false,
                };
            });
        });

        const routesById = new Map(this.network.routes.map(route => [route.id, route]));
        const stations = {};
        this.network.stations.forEach(station => {
            const heights = stationHeights.get(station.id);
            if (!heights) return; // only stations that a route stops at
            const lineKeys = new Set(station.routes.map(id => routesById.get(id)).filter(route => route && !route.hidden).map(routeKey));
            const dots = [...new Set(station.routes.map(id => routesById.get(id)).filter(route => route && !route.hidden).map(route => route.color))]
                .map(color => `<i style="background:#${color}"></i>`).join("");
            const interchange = lineKeys.size >= 2;
            const y = heights.reduce((sum, value) => sum + value, 0) / heights.length;
            stations[station.id] = {
                type: "html",
                classes: [],
                label: pickName(station.name, settings.nameMode),
                position: {x: station.x, y: y + 2, z: station.z},
                anchor: {x: 0, y: 0},
                html: `<div class="atr-station${interchange ? " atr-interchange" : ""}" data-id="${station.id}" style="--atr-label:${settings.labelColor}">` +
                    `<span class="atr-dots">${dots}</span><span class="atr-name">${escapeHtml(pickName(station.name, settings.nameMode))}</span></div>`,
                maxDistance: (interchange ? 3000 : 1200) * settings.labelDistance,
                listed: true,
            };
        });

        this.lineSet.updateMarkersFromData(lines);
        this.stationSet.updateMarkersFromData(stations);
        this.app.mapViewer.redraw();
    }

    drawVehicles(vehicles) {
        const settings = this.settings;
        const now = performance.now();
        const trains = {};
        const seen = new Set();
        vehicles.forEach(vehicle => {
            if (!vehicle.onRoute) return;
            seen.add(vehicle.id);
            const [x, y, z] = vehicle.head;
            const color = settings.trainColor || `#${vehicle.color}`;
            const text = escapeHtml(vehicle.routeNumber || "");
            trains[vehicle.id] = {
                type: "html",
                classes: [],
                label: `${pickName(vehicle.routeName, settings.nameMode)} to ${pickName(vehicle.destination, settings.nameMode)}`,
                position: {x, y: y + 1, z},
                anchor: {x: 0, y: 0},
                html: `<div class="atr-train${vehicle.moving ? "" : " atr-stopped"}${vehicle.suspended ? " atr-suspended" : ""}" style="--atr-train:${vehicle.suspended ? "#888" : color};--atr-size:${settings.trainSize}" title="${escapeHtml((vehicle.suspended ? "Held at depot: " : "") + pickName(vehicle.destination, settings.nameMode))}">${text}</div>`,
                listed: false,
            };
            const motion = this.trainMotion.get(vehicle.id);
            const target = new Vector3(x, y + 1, z);
            if (motion) {
                motion.from.copy(motion.current);
                motion.to.copy(target);
                motion.start = now;
            } else {
                this.trainMotion.set(vehicle.id, {from: target.clone(), to: target.clone(), current: target.clone(), start: now});
            }
        });
        [...this.trainMotion.keys()].forEach(id => seen.has(id) || this.trainMotion.delete(id));
        this.trainSet.updateMarkersFromData(trains);
        // keep the interpolated position, not the jump updateFromData just made
        this.trainSet.markers.forEach((marker, id) => {
            const motion = this.trainMotion.get(id);
            if (motion) marker.position.copy(motion.current);
        });
    }

    /**
     * Glides trains between two updates instead of jumping once a second.
     */
    animate(now) {
        requestAnimationFrame(this.animate);
        if (this.trainMotion.size === 0 || !this.trainSet.visible) return;
        this.trainMotion.forEach((motion, id) => {
            const t = Math.min(1, (now - motion.start) / VEHICLES_INTERVAL);
            motion.current.lerpVectors(motion.from, motion.to, t);
            const marker = this.trainSet.markers.get(id);
            if (marker) marker.position.copy(motion.current);
        });
        this.app.mapViewer.redraw();
    }
}

function dimensionOfMap(mapId) {
    const id = (mapId || "").toLowerCase();
    if (id.includes("nether")) return "minecraft/the_nether";
    if (id.includes("end")) return "minecraft/the_end";
    return "minecraft/overworld";
}

function hexToRgb(hex) {
    const value = parseInt(hex, 16) || 0;
    return {r: (value >> 16) & 255, g: (value >> 8) & 255, b: value & 255};
}

function loadSettings() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_KEY)) || {};
    } catch (e) {
        return {};
    }
}

function saveSettings(settings) {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
    } catch (e) {
        // private mode: settings last for this visit only
    }
}

/*
 * AureliaMap: the arrivals panel that opens when an ATR station is clicked.
 * Next train first (destination big, one countdown, platform), then the following trains.
 */
import {arrivalRows} from "./AtrArrivals.mjs";
import {escapeHtml, pickName} from "./AtrNames.mjs";

const REFRESH_INTERVAL = 5000;
const MAX_ROWS = 8;

export class AtrArrivalsPanel {

    /**
     * @param layer {AtrLayer}
     */
    constructor(layer) {
        this.layer = layer;
        this.stationId = null;
        this.response = null;
        this.receivedAt = 0;
        this.timers = [];

        this.element = document.createElement("div");
        this.element.className = "atr-panel";
        this.element.hidden = true;
        this.element.addEventListener("click", event => {
            if (event.target.closest(".atr-panel-close")) this.close();
            event.stopPropagation();
        });
        document.addEventListener("keydown", event => {
            if (event.key === "Escape") this.close();
        });
    }

    open(stationId) {
        this.close();
        // attached on first use: Vue replaces #app's content when it mounts, after this panel is made
        const host = document.getElementById("app") || document.body;
        if (this.element.parentNode !== host) host.appendChild(this.element);
        this.stationId = stationId;
        this.response = null;
        this.element.hidden = false;
        this.render();
        this.refresh();
        this.timers.push(setInterval(() => this.refresh(), REFRESH_INTERVAL));
        this.timers.push(setInterval(() => this.render(), 1000));
    }

    close() {
        this.timers.forEach(timer => clearInterval(timer));
        this.timers = [];
        this.stationId = null;
        this.element.hidden = true;
    }

    async refresh() {
        const stationId = this.stationId;
        if (!stationId) return;
        try {
            const response = await this.layer.fetchJson("/mtr/api/map/arrivals", {stationIdsHex: [stationId], maxCountPerPlatform: 5});
            if (stationId !== this.stationId) return;
            this.response = response;
            this.receivedAt = Date.now();
        } catch (e) {
            console.warn("ATR layer: arrivals failed", e);
        }
        this.render();
    }

    render() {
        if (!this.stationId) return;
        const nameMode = this.layer.settings.nameMode;
        const station = this.layer.network && this.layer.network.stations.find(station => station.id === this.stationId);
        const name = station ? pickName(station.name, nameMode) : "";
        const otherNames = station ? pickName(station.name, "all").split(" / ").filter(line => line !== name) : [];
        const rows = this.response ? arrivalRows(this.response, this.receivedAt, Date.now(), nameMode).slice(0, MAX_ROWS) : [];

        let body;
        if (!this.response) {
            body = `<p class="atr-panel-empty">Loading arrivals…</p>`;
        } else if (rows.length === 0) {
            body = `<p class="atr-panel-empty">No trains in the next hour.</p>`;
        } else {
            const [next, ...later] = rows;
            body = `<div class="atr-next" style="--atr-route:${next.color}">
                    <div class="atr-next-label">Next train${next.scheduled ? " (scheduled)" : ""}</div>
                    <div class="atr-next-destination">${escapeHtml(next.destination)}</div>
                    <div class="atr-next-meta">
                        <span class="atr-next-countdown">${next.countdown}</span>
                        ${next.platform ? `<span class="atr-platform">Platform ${escapeHtml(next.platform)}</span>` : ""}
                    </div>
                    <div class="atr-next-route">${escapeHtml([next.number, next.route].filter(Boolean).join(" "))}</div>
                </div>` +
                (later.length === 0 ? "" : `<ol class="atr-rows">${later.map(row => `<li style="--atr-route:${row.color}">
                    <span class="atr-row-destination">${escapeHtml(row.destination)}</span>
                    <span class="atr-row-platform">${row.platform ? escapeHtml(row.platform) : ""}</span>
                    <span class="atr-row-countdown">${row.countdown}</span>
                </li>`).join("")}</ol>`);
        }

        this.element.innerHTML = `<div class="atr-panel-head">
                <div>
                    <div class="atr-panel-title">${escapeHtml(name)}</div>
                    ${otherNames.length ? `<div class="atr-panel-subtitle">${escapeHtml(otherNames.join(" · "))}</div>` : ""}
                </div>
                <button class="atr-panel-close" aria-label="Close">×</button>
            </div>${body}`;
    }
}

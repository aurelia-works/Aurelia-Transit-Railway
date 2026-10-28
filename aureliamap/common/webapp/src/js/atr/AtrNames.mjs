/*
 * MTR names hold one line per language separated by "|", and extra text (like a direction) after "||".
 */
const LATIN = /[A-Za-z]/;
const CODE = /^[A-Z]{1,4}\d+$/;

/**
 * @param name {string} an MTR name, like "奧蕾莉亞中央車站|Aurelia Centraal"
 * @param mode {"latin"|"first"|"all"} latin = the longest line with Latin letters that is not a code like "OGL08"
 * @returns {string}
 */
export function pickName(name, mode) {
    const lines = String(name || "").split("||")[0].split("|").map(line => line.trim()).filter(line => line.length > 0);
    if (lines.length === 0) return "";
    if (mode === "all") return lines.join(" / ");
    if (mode === "first") return lines[0];
    const latin = lines.filter(line => LATIN.test(line) && !CODE.test(line));
    return latin.length > 0 ? latin.reduce((longest, line) => line.length > longest.length ? line : longest) : lines[0];
}

/**
 * One key per line, so both directions of a route count as one line at a station.
 * @param route {{name: string, color: string}}
 */
export function routeKey(route) {
    return String(route.name || "").split("||")[0] + "#" + route.color;
}

export function escapeHtml(text) {
    return String(text).replace(/[&<>"']/g, c => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[c]));
}

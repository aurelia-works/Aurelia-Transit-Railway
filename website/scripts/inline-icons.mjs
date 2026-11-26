// Writes src/generated/material-icons.css with the Material Icons font inside it as a data URL, so the Resource Pack
// Creator shows its icons without internet. The game serves the page from text embedded in the jar
// (WebserverResources), which cannot carry a separate binary font file.
import {mkdirSync, readFileSync, writeFileSync} from "node:fs";
import {createRequire} from "node:module";
import {dirname, join} from "node:path";
import {fileURLToPath} from "node:url";

const require = createRequire(import.meta.url);
const font = readFileSync(require.resolve("material-icons/iconfont/material-icons.woff2")).toString("base64");
const output = join(dirname(fileURLToPath(import.meta.url)), "../src/generated/material-icons.css");
mkdirSync(dirname(output), {recursive: true});
writeFileSync(output, `/* Material Icons, Apache License 2.0, from the material-icons package; written by scripts/inline-icons.mjs */
@font-face{font-family:"Material Icons";font-style:normal;font-weight:400;font-display:block;src:url(data:font/woff2;base64,${font}) format("woff2")}
.material-icons{font-family:"Material Icons";font-weight:normal;font-style:normal;font-size:24px;line-height:1;letter-spacing:normal;text-transform:none;display:inline-block;white-space:nowrap;word-wrap:normal;direction:ltr;-webkit-font-feature-settings:"liga";font-feature-settings:"liga";-webkit-font-smoothing:antialiased}
`);

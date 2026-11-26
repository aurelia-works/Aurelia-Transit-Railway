/**
 * Readies model files for the Resource Pack Creator's upload, in the browser, before they reach the game.
 *
 * - Blender's glTF export (.glb, or .gltf with everything embedded) becomes an OBJ, its MTL and one PNG per texture, the
 *   formats the game's model loader reads. Each glTF node with a mesh becomes one OBJ group, so it is one model part.
 * - Every file name is made into a valid resource path (lower case, a-z 0-9 _ - only, one dot before the extension), the
 *   way the game reads the names, so "Train Body.png" and "Image.001.png" no longer fail to load.
 * - An MTL's texture paths and an OBJ's mtllib are cut to the file name: the creator keeps every file in one folder, and
 *   Blender can write absolute or "../" paths there.
 */

const GLB_MAGIC = 0x46546c67;
const GLB_CHUNK_JSON = 0x4e4f534a;
const GLB_CHUNK_BIN = 0x004e4942;
const TRIANGLES = 4;
const COMPONENT_COUNTS: Record<string, number> = {SCALAR: 1, VEC2: 2, VEC3: 3, VEC4: 4, MAT4: 16};

export async function prepareModelFiles(files: File[]): Promise<File[]> {
	const prepared: File[] = [];
	for (const file of files) {
		const [baseName, extension] = splitName(file.name);
		const name = formatName(baseName);
		switch (extension) {
			case "glb":
			case "gltf":
				prepared.push(...await convertGltf(name, extension === "glb" ? parseGlb(await file.arrayBuffer()) : {json: JSON.parse(await file.text())}));
				break;
			case "mtl":
				prepared.push(new File([rewriteMtl(await file.text())], `${name}.mtl`));
				break;
			case "obj":
				prepared.push(new File([rewriteObj(await file.text())], `${name}.obj`));
				break;
			default:
				prepared.push(new File([file], `${name}.${extension}`));
				break;
		}
	}
	return prepared;
}

/**
 * The game's {@code CustomResourceTools.formatIdentifierString}, without the slash (no folders here) and without the dot:
 * the game cuts a resource path at its first dot, so Blender's "Image.001" would be looked up as "image".
 */
export function formatName(name: string) {
	return name.toLowerCase().replace(/[^a-z0-9_-]/g, "_");
}

function splitName(fileName: string): [string, string] {
	const index = fileName.lastIndexOf(".");
	return index < 0 ? [fileName, ""] : [fileName.substring(0, index), fileName.substring(index + 1).toLowerCase()];
}

function fileNameOnly(path: string) {
	const parts = path.trim().replace(/^"|"$/g, "").split(/[\\/]/);
	return parts[parts.length - 1];
}

function formatFileName(path: string) {
	const [baseName, extension] = splitName(fileNameOnly(path));
	return extension ? `${formatName(baseName)}.${extension}` : formatName(baseName);
}

function rewriteMtl(content: string) {
	return content.split(/\r?\n/).map(line => {
		const match = line.match(/^(\s*map_\w+)\s+(.*)$/i);
		if (!match) {
			return line;
		}
		// The path is the rest of the line and may hold spaces ("C:\\Users\\Jo\\Body Texture.png"); options such as "-bm 1.0"
		// can come first, so without a folder in the path the path is the last word
		const rest = match[2].trim();
		const options = /[\\/]/.test(rest) || !rest.startsWith("-") ? "" : rest.substring(0, rest.lastIndexOf(" ") + 1);
		return `${match[1]} ${options}${formatFileName(rest.substring(options.length))}`;
	}).join("\n");
}

function rewriteObj(content: string) {
	return content.split(/\r?\n/).map(line => {
		const match = line.match(/^(\s*mtllib)\s+(.*)$/);
		return match ? `${match[1]} ${formatFileName(match[2])}` : line;
	}).join("\n");
}

interface Gltf {
	json: any;
	bin?: ArrayBuffer;
}

function parseGlb(buffer: ArrayBuffer): Gltf {
	const view = new DataView(buffer);
	if (buffer.byteLength < 20 || view.getUint32(0, true) !== GLB_MAGIC) {
		throw new Error("This .glb file is damaged.");
	}
	let json: any;
	let bin: ArrayBuffer | undefined;
	let offset = 12;
	while (offset + 8 <= buffer.byteLength) {
		const length = view.getUint32(offset, true);
		const type = view.getUint32(offset + 4, true);
		const data = buffer.slice(offset + 8, offset + 8 + length);
		if (type === GLB_CHUNK_JSON) {
			json = JSON.parse(new TextDecoder().decode(data));
		} else if (type === GLB_CHUNK_BIN && !bin) {
			bin = data;
		}
		offset += 8 + length;
	}
	if (!json) {
		throw new Error("This .glb file is damaged.");
	}
	return {json, bin};
}

async function convertGltf(name: string, gltf: Gltf): Promise<File[]> {
	const json = gltf.json;
	const unsupported = (json.extensionsRequired ?? []) as string[];
	if (unsupported.length > 0) {
		throw new Error(`Export the glTF again without ${unsupported.join(", ")} (in Blender, turn off compression).`);
	}
	// Blender writes this for a Mapping node; OBJ has no texture transform, so the texture would come out stretched or moved
	if (((json.extensionsUsed ?? []) as string[]).includes("KHR_texture_transform")) {
		throw new Error("This model moves or scales its texture (a Mapping node in Blender). Bake the texture without it, then export again.");
	}

	const buffers = await Promise.all((json.buffers ?? []).map((buffer: any, index: number) => loadBuffer(buffer, index, gltf.bin)));
	const bufferView = (index: number) => {
		const view = json.bufferViews[index];
		return {buffer: buffers[view.buffer] as ArrayBuffer, byteOffset: view.byteOffset ?? 0, byteLength: view.byteLength, byteStride: view.byteStride as number | undefined};
	};
	const readAccessor = (index: number) => readAccessorData(json.accessors[index], bufferView);

	// One material per glTF material, plus "default" for primitives without one
	const outputFiles: File[] = [];
	const mtl: string[] = ["newmtl default", "Kd 1.000000 1.000000 1.000000", "d 1.000000", ""];
	const textureFiles = new Map<number, string>();
	const materials: any[] = json.materials ?? [];
	for (let i = 0; i < materials.length; i++) {
		const pbr = materials[i].pbrMetallicRoughness ?? {};
		const color: number[] = pbr.baseColorFactor ?? [1, 1, 1, 1];
		mtl.push(`newmtl material_${i}`, `Kd ${color.slice(0, 3).map(value => value.toFixed(6)).join(" ")}`, `d ${color[3].toFixed(6)}`);
		const textureIndex = pbr.baseColorTexture?.index;
		if (textureIndex !== undefined && json.textures?.[textureIndex]?.source !== undefined) {
			const imageIndex: number = json.textures[textureIndex].source;
			if (!textureFiles.has(imageIndex)) {
				// "_gltf_" so the generated names don't replace textures the user uploaded as name_0.png and so on
				const fileName = `${name}_gltf_${textureFiles.size}.png`;
				outputFiles.push(new File([await toPng(await loadImage(json.images[imageIndex], bufferView))], fileName, {type: "image/png"}));
				textureFiles.set(imageIndex, fileName);
			}
			mtl.push(`map_Kd ${textureFiles.get(imageIndex)}`);
		}
		mtl.push("");
	}

	const obj: string[] = [`# Converted from glTF by Aurelia Transit Railway's Resource Pack Creator`, `mtllib ${name}.mtl`];
	const usedNames = new Map<string, number>();
	// OBJ numbers positions, texture coordinates and normals separately, and a primitive may lack the last two
	let vertexCount = 0;
	let uvCount = 0;
	let normalCount = 0;
	let faceCount = 0;

	const visit = (nodeIndex: number, parentMatrix: number[]) => {
		const node = json.nodes[nodeIndex];
		const matrix = multiply(parentMatrix, nodeMatrix(node));
		if (node.mesh !== undefined) {
			const mesh = json.meshes[node.mesh];
			const baseName = formatName(node.name ?? mesh.name ?? "part") || "part";
			const count = usedNames.get(baseName) ?? 0;
			usedNames.set(baseName, count + 1);
			obj.push(`g ${count === 0 ? baseName : `${baseName}_${count + 1}`}`);
			const normalMatrix = normalMatrixOf(matrix);
			const flipWinding = determinant3(matrix) < 0;

			for (const primitive of mesh.primitives) {
				if ((primitive.mode ?? TRIANGLES) !== TRIANGLES) {
					continue;
				}
				const positions = readAccessor(primitive.attributes.POSITION);
				const normals = primitive.attributes.NORMAL === undefined ? undefined : readAccessor(primitive.attributes.NORMAL);
				const uvs = primitive.attributes.TEXCOORD_0 === undefined ? undefined : readAccessor(primitive.attributes.TEXCOORD_0);
				const vertices = positions.length / 3;

				for (let i = 0; i < vertices; i++) {
					const [x, y, z] = transformPoint(matrix, positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
					obj.push(`v ${format(x)} ${format(y)} ${format(z)}`);
				}
				if (uvs) {
					// glTF puts V = 0 at the top of the image, OBJ at the bottom
					for (let i = 0; i < vertices; i++) {
						obj.push(`vt ${format(uvs[i * 2])} ${format(1 - uvs[i * 2 + 1])}`);
					}
				}
				if (normals) {
					for (let i = 0; i < vertices; i++) {
						const [x, y, z] = transformNormal(normalMatrix, normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
						obj.push(`vn ${format(x)} ${format(y)} ${format(z)}`);
					}
				}

				obj.push(`usemtl ${primitive.material === undefined ? "default" : `material_${primitive.material}`}`);
				const indices = primitive.indices === undefined ? Array.from({length: vertices}, (_, i) => i) : readAccessor(primitive.indices);
				const corner = (index: number) => {
					const v = vertexCount + index + 1;
					const vt = uvCount + index + 1;
					const vn = normalCount + index + 1;
					return uvs ? (normals ? `${v}/${vt}/${vn}` : `${v}/${vt}`) : (normals ? `${v}//${vn}` : `${v}`);
				};
				for (let i = 0; i + 2 < indices.length; i += 3) {
					const a = corner(indices[i]);
					const b = corner(indices[i + 1]);
					const c = corner(indices[i + 2]);
					obj.push(flipWinding ? `f ${a} ${c} ${b}` : `f ${a} ${b} ${c}`);
					faceCount++;
				}
				vertexCount += vertices;
				uvCount += uvs ? vertices : 0;
				normalCount += normals ? vertices : 0;
			}
		}
		(node.children ?? []).forEach((child: number) => visit(child, matrix));
	};

	const scene = json.scenes?.[json.scene ?? 0];
	const roots: number[] = scene?.nodes ?? (json.nodes ?? []).map((_: any, index: number) => index).filter((index: number) => !(json.nodes as any[]).some(node => (node.children ?? []).includes(index)));
	roots.forEach(root => visit(root, IDENTITY));

	if (faceCount === 0) {
		throw new Error("This glTF file has no triangles to import.");
	}

	outputFiles.unshift(new File([obj.join("\n") + "\n"], `${name}.obj`), new File([mtl.join("\n")], `${name}.mtl`));
	return outputFiles;
}

async function loadBuffer(buffer: any, index: number, bin?: ArrayBuffer): Promise<ArrayBuffer> {
	if (buffer.uri === undefined) {
		if (index === 0 && bin) {
			return bin;
		}
		throw new Error("This glTF file is missing its binary data.");
	}
	if (buffer.uri.startsWith("data:")) {
		return (await fetch(buffer.uri)).arrayBuffer();
	}
	throw new Error("This .gltf keeps its data in separate files. Export it as glTF Binary (.glb) instead.");
}

function readAccessorData(accessor: any, bufferView: (index: number) => { buffer: ArrayBuffer, byteOffset: number, byteLength: number, byteStride?: number }): number[] {
	if (accessor.sparse) {
		throw new Error("This glTF file uses sparse data, which the importer does not read. Apply shape keys and modifiers before exporting.");
	}
	const components = COMPONENT_COUNTS[accessor.type];
	const result = new Array<number>(accessor.count * components).fill(0);
	if (accessor.bufferView === undefined) {
		return result;
	}

	const view = bufferView(accessor.bufferView);
	const data = new DataView(view.buffer, view.byteOffset);
	const componentSize = accessor.componentType === 5126 || accessor.componentType === 5125 ? 4 : accessor.componentType === 5122 || accessor.componentType === 5123 ? 2 : 1;
	const stride = view.byteStride ?? componentSize * components;
	const start = accessor.byteOffset ?? 0;
	const normalized = accessor.normalized === true;

	for (let i = 0; i < accessor.count; i++) {
		for (let j = 0; j < components; j++) {
			const offset = start + i * stride + j * componentSize;
			let value: number;
			switch (accessor.componentType) {
				case 5120:
					value = data.getInt8(offset);
					value = normalized ? Math.max(value / 127, -1) : value;
					break;
				case 5121:
					value = data.getUint8(offset);
					value = normalized ? value / 255 : value;
					break;
				case 5122:
					value = data.getInt16(offset, true);
					value = normalized ? Math.max(value / 32767, -1) : value;
					break;
				case 5123:
					value = data.getUint16(offset, true);
					value = normalized ? value / 65535 : value;
					break;
				case 5125:
					value = data.getUint32(offset, true);
					break;
				default:
					value = data.getFloat32(offset, true);
					break;
			}
			result[i * components + j] = value;
		}
	}
	return result;
}

async function loadImage(image: any, bufferView: (index: number) => { buffer: ArrayBuffer, byteOffset: number, byteLength: number }): Promise<Blob> {
	if (image.bufferView !== undefined) {
		const view = bufferView(image.bufferView);
		return new Blob([view.buffer.slice(view.byteOffset, view.byteOffset + view.byteLength)], {type: image.mimeType ?? "image/png"});
	}
	if (image.uri?.startsWith("data:")) {
		return (await fetch(image.uri)).blob();
	}
	throw new Error("This .gltf keeps its textures in separate files. Export it as glTF Binary (.glb) instead.");
}

/** The game reads PNG only, so a JPEG texture is drawn once and saved as PNG. */
async function toPng(blob: Blob): Promise<Blob> {
	if (blob.type === "image/png") {
		return blob;
	}
	const bitmap = await createImageBitmap(blob);
	const canvas = document.createElement("canvas");
	canvas.width = bitmap.width;
	canvas.height = bitmap.height;
	canvas.getContext("2d")?.drawImage(bitmap, 0, 0);
	bitmap.close();
	return new Promise((resolve, reject) => canvas.toBlob(png => png ? resolve(png) : reject(new Error("A texture could not be converted to PNG.")), "image/png"));
}

function format(value: number) {
	return (Math.round(value * 1e6) / 1e6).toString();
}

// Column-major 4x4 matrices, as glTF stores them
const IDENTITY = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1];

function nodeMatrix(node: any): number[] {
	if (node.matrix) {
		return node.matrix;
	}
	const [tx, ty, tz] = node.translation ?? [0, 0, 0];
	const [qx, qy, qz, qw] = node.rotation ?? [0, 0, 0, 1];
	const [sx, sy, sz] = node.scale ?? [1, 1, 1];
	return [
		(1 - 2 * (qy * qy + qz * qz)) * sx, 2 * (qx * qy + qz * qw) * sx, 2 * (qx * qz - qy * qw) * sx, 0,
		2 * (qx * qy - qz * qw) * sy, (1 - 2 * (qx * qx + qz * qz)) * sy, 2 * (qy * qz + qx * qw) * sy, 0,
		2 * (qx * qz + qy * qw) * sz, 2 * (qy * qz - qx * qw) * sz, (1 - 2 * (qx * qx + qy * qy)) * sz, 0,
		tx, ty, tz, 1,
	];
}

function multiply(a: number[], b: number[]): number[] {
	const result = new Array<number>(16);
	for (let column = 0; column < 4; column++) {
		for (let row = 0; row < 4; row++) {
			let sum = 0;
			for (let k = 0; k < 4; k++) {
				sum += a[k * 4 + row] * b[column * 4 + k];
			}
			result[column * 4 + row] = sum;
		}
	}
	return result;
}

function transformPoint(m: number[], x: number, y: number, z: number): [number, number, number] {
	return [m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14]];
}

function determinant3(m: number[]) {
	return m[0] * (m[5] * m[10] - m[9] * m[6]) - m[4] * (m[1] * m[10] - m[9] * m[2]) + m[8] * (m[1] * m[6] - m[5] * m[2]);
}

/** The inverse transpose of the matrix's 3x3 part, row-major, so normals stay square to scaled faces. */
function normalMatrixOf(m: number[]): number[] {
	const a = m[0], b = m[4], c = m[8], d = m[1], e = m[5], f = m[9], g = m[2], h = m[6], i = m[10];
	const det = determinant3(m) || 1;
	// (inverse)^T: the cofactor matrix divided by the determinant
	return [
		(e * i - f * h) / det, -(d * i - f * g) / det, (d * h - e * g) / det,
		-(b * i - c * h) / det, (a * i - c * g) / det, -(a * h - b * g) / det,
		(b * f - c * e) / det, -(a * f - c * d) / det, (a * e - b * d) / det,
	];
}

function transformNormal(n: number[], x: number, y: number, z: number): [number, number, number] {
	const nx = n[0] * x + n[1] * y + n[2] * z;
	const ny = n[3] * x + n[4] * y + n[5] * z;
	const nz = n[6] * x + n[7] * y + n[8] * z;
	const length = Math.hypot(nx, ny, nz) || 1;
	return [nx / length, ny / length, nz / length];
}

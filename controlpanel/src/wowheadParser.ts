import type { ScrapedRecipe } from "./types";

/**
 * Extracts the `listviewspells` JSON array from Wowhead listing page HTML.
 * This is a TypeScript port of the Java WowheadPageParser logic.
 */
export function parseRecipesFromHtml(html: string): Omit<ScrapedRecipe, "selected" | "status">[] {
    const jsonArray = extractJsonArray(html, "listviewspells");
    if (!jsonArray) {
        throw new Error("No listviewspells data found in the HTML.");
    }

    const normalized = fixJavaScriptObjectNotation(jsonArray);

    let entries: unknown[];
    try {
        entries = JSON.parse(normalized);
    } catch {
        throw new Error("Failed to parse listviewspells JSON.");
    }

    const results: Omit<ScrapedRecipe, "selected" | "status">[] = [];

    for (const entry of entries) {
        const obj = entry as Record<string, unknown>;

        // Skip non-crafting spells (no output item)
        if (!obj.creates || !Array.isArray(obj.creates)) continue;

        const creates = obj.creates as number[];
        const spellId = obj.id as number;
        const name = (obj.name as string) ?? "Unknown Recipe";
        const outputItemId = creates[0];
        const outputQuantity = creates.length > 1 ? creates[1] : 1;

        const reagents: { itemId: number; quantity: number }[] = [];
        if (Array.isArray(obj.reagents)) {
            for (const r of obj.reagents as number[][]) {
                const itemId = r[0];
                const quantity = r.length > 1 ? r[1] : 1;
                reagents.push({ itemId, quantity });
            }
        }

        results.push({ spellId, name, outputItemId, outputQuantity, reagents });
    }

    return results;
}

/**
 * Finds a top-level JSON array assigned to the named JS variable
 * by bracket-counting (handles nested arrays/objects safely).
 */
function extractJsonArray(html: string, variableName: string): string | null {
    const marker = html.indexOf(variableName);
    if (marker < 0) return null;

    const bracketStart = html.indexOf("[", marker);
    if (bracketStart < 0) return null;

    let depth = 0;
    let inString = false;
    let escaped = false;

    for (let i = bracketStart; i < html.length; i++) {
        const c = html[i];

        if (escaped) { escaped = false; continue; }
        if (c === "\\" && inString) { escaped = true; continue; }
        if (c === '"') { inString = !inString; continue; }
        if (inString) continue;

        if (c === "[") depth++;
        else if (c === "]") {
            depth--;
            if (depth === 0) {
                return html.substring(bracketStart, i + 1);
            }
        }
    }

    return null; // unbalanced brackets
}

/**
 * Ensures every bare object key in a JS expression is double-quoted
 * so it becomes valid JSON.
 */
function fixJavaScriptObjectNotation(js: string): string {
    return js.replace(/(?<=[{,])\s*([a-zA-Z_$][a-zA-Z0-9_$]*)\s*:/g, '"$1":');
}

import type { Item } from "./types";

const baseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, options?: RequestInit): Promise<T> {
    const response = await fetch(`${baseUrl}${path}`, {
        headers: {
            "Content-Type": "application/json"
        },
        ...options
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `Request failed: ${response.status}`);
    }

    // Read raw text first — handles empty bodies (204 No Content) safely
    const text = await response.text();
    if (!text) {
        // no content to parse
        return undefined as unknown as T;
    }

    const contentType = (response.headers.get("content-type") || "").toLowerCase();
    if (contentType.includes("application/json")) {
        try {
            return JSON.parse(text) as T;
        } catch (err) {
            throw new Error("Failed to parse response JSON: " + (err instanceof Error ? err.message : String(err)));
        }
    }

    // Not JSON — return raw text
    return text as unknown as T;
}

export async function getItems(): Promise<Item[]> {
    return request<Item[]>("/items");
}

export async function createItem(item: Item): Promise<Item> {
    const payload = { ...item, finishingIngredient: item.finishingIngredient ?? false };
    return request<Item>("/items", {
        method: "POST",
        body: JSON.stringify(payload)
    });
}

export async function updateItem(id: number, item: Item): Promise<Item> {
    const payload = { ...item, finishingIngredient: item.finishingIngredient ?? false };
    return request<Item>(`/items/${id}`, {
        method: "PUT",
        body: JSON.stringify(payload)
    });
}

export async function deleteItem(id: number): Promise<void> {
    await request<void>(`/items/${id}`, {
        method: "DELETE"
    });
}

export async function archiveLogs(): Promise<void> {
    await request<void>("/logs/archive", { method: "POST" });
}

export async function clearLogs(): Promise<void> {
    await request<void>("/logs/clear", { method: "POST" });
}

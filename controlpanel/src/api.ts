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

    return response.json() as Promise<T>;
}

export async function getItems(): Promise<Item[]> {
    return request<Item[]>("/items");
}

export async function createItem(item: Item): Promise<Item> {
    return request<Item>("/items", {
        method: "POST",
        body: JSON.stringify(item)
    });
}

export async function updateItem(id: number, item: Item): Promise<Item> {
    return request<Item>(`/items/${id}`, {
        method: "PUT",
        body: JSON.stringify(item)
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

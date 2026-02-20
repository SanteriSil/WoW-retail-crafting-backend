import type { AuthResponse, Item, Profession } from "./types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

// ── Token helpers ──

const TOKEN_KEY = "auth_token";
const USER_KEY = "auth_user";

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): { discordUsername: string; avatarUrl: string | null } | null {
    try {
        const raw = localStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}

export function setAuth(auth: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, auth.token);
    localStorage.setItem(USER_KEY, JSON.stringify({ discordUsername: auth.discordUsername, avatarUrl: auth.avatarUrl }));
}

export function clearAuth(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    // clean up legacy endpoint keys
    localStorage.removeItem("target_host");
    localStorage.removeItem("target_port");
    localStorage.removeItem("target_use_localhost");
}

// ── Core request helper ──

async function request<T>(path: string, options?: RequestInit): Promise<T> {
    const method = (options?.method ?? "GET").toString().toUpperCase();

    const defaultHeaders: Record<string, string> = {};
    if (options?.body || ["POST", "PUT", "PATCH"].includes(method)) {
        defaultHeaders["Content-Type"] = "application/json";
    }

    // Attach JWT if available
    const token = getToken();
    if (token) {
        defaultHeaders["Authorization"] = `Bearer ${token}`;
    }

    const response = await fetch(`${BASE_URL}${path}`, {
        headers: {
            ...defaultHeaders,
            ...(options?.headers as Record<string, string> | undefined)
        },
        ...options
    });

    // Auto-logout on 401
    if (response.status === 401) {
        clearAuth();
        window.location.reload();
        throw new Error("Session expired — please log in again.");
    }

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `Request failed: ${response.status}`);
    }

    const text = await response.text();
    if (!text) {
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

    return text as unknown as T;
}

// ── Auth ──

export async function exchangeDiscordCode(code: string, redirectUri: string): Promise<AuthResponse> {
    return request<AuthResponse>("/auth/discord/callback", {
        method: "POST",
        body: JSON.stringify({ code, redirectUri })
    });
}

export async function getItems(): Promise<Item[]> {
    return request<Item[]>("/items");
}

export async function getProfessions(): Promise<Profession[]> {
    return request<Profession[]>("/professions");
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

export async function getCurrentLogs(): Promise<string> {
    return request<string>("/logs/current", { method: "GET" });
}

export async function fetchCraftingAH(): Promise<string | void> {
    // Controller exposes GET /craftingAH/fetch — use GET to avoid unnecessary preflight
    return request<string>("/craftingAH/fetch", { method: "GET" });
}

export async function getItemIds(): Promise<number[]> {
    return request<number[]>("/items/ids");
}

export async function submitAuctionData(csv: string): Promise<string> {
    return request<string>("/craftingAH/submit", {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: csv
    });
}

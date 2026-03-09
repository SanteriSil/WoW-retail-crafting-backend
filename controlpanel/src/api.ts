import type { AccessRequest, AllowedUser, AuthResponse, Character, CreateCharacterRequest, DashboardResponse, Expansion, ImportResult, Item, Page, Profession, RecipeDetail, RecipeFilterParams, RecipeImportCommand, RecipeItemIdsResponse, RecipePriceRefreshResponse, RecipeSummary, RecipeWritePayload, UpdateCharacterRequest } from "./types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

// ── Token helpers ──

const TOKEN_KEY = "auth_token";
const USER_KEY = "auth_user";

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): { discordUsername: string; avatarUrl: string | null; role: string | null } | null {
    try {
        const raw = localStorage.getItem(USER_KEY);
        return raw ? JSON.parse(raw) : null;
    } catch {
        return null;
    }
}

export function setAuth(auth: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, auth.token);
    localStorage.setItem(USER_KEY, JSON.stringify({ discordUsername: auth.discordUsername, avatarUrl: auth.avatarUrl, role: auth.role }));
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
        ...options,
        headers: {
            ...defaultHeaders,
            ...(options?.headers as Record<string, string> | undefined)
        }
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

/**
 * Dev-only: bypass Discord OAuth by calling the backend dev login endpoint.
 * Only works when the backend is running with the "dev" Spring profile.
 */
export async function devLogin(): Promise<AuthResponse> {
    return request<AuthResponse>("/auth/dev/login", { method: "POST" });
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

// ── User management ──

export async function getAllowedUsers(): Promise<AllowedUser[]> {
    return request<AllowedUser[]>("/auth/users");
}

export async function addAllowedUser(discordId: string, discordUsername: string): Promise<AllowedUser> {
    return request<AllowedUser>("/auth/users", {
        method: "POST",
        body: JSON.stringify({ discordId, discordUsername })
    });
}

export async function removeAllowedUser(discordId: string): Promise<void> {
    await request<void>(`/auth/users/${discordId}`, { method: "DELETE" });
}

export async function promoteUser(discordId: string): Promise<AllowedUser> {
    return request<AllowedUser>(`/auth/users/${discordId}/promote`, { method: "POST" });
}

export async function demoteUser(discordId: string): Promise<AllowedUser> {
    return request<AllowedUser>(`/auth/users/${discordId}/demote`, { method: "POST" });
}

// ── Access requests ──────────────────────────────────────────────────────────

export async function requestAccess(discordId: string, discordUsername: string): Promise<void> {
    await request<void>("/auth/access-requests", {
        method: "POST",
        body: JSON.stringify({ discordId, discordUsername }),
    });
}

export async function getAccessRequests(): Promise<AccessRequest[]> {
    return request<AccessRequest[]>("/auth/access-requests");
}

export async function approveAccessRequest(id: number): Promise<void> {
    await request<void>(`/auth/access-requests/${id}/approve`, { method: "POST" });
}

export async function denyAccessRequest(id: number): Promise<void> {
    await request<void>(`/auth/access-requests/${id}/deny`, { method: "POST" });
}

// ── Expansions ────────────────────────────────────────────────────────────────

export async function getExpansions(): Promise<Expansion[]> {
    return request<Expansion[]>("/expansions");
}

// ── Recipes ───────────────────────────────────────────────────────────────────

function buildRecipeQuery(params: RecipeFilterParams): string {
    const query = new URLSearchParams();
    if (params.page != null) query.set("page", String(params.page));
    if (params.size != null) query.set("size", String(params.size));
    if (params.sort) query.set("sort", params.sort);
    if (params.professionId != null) query.set("professionId", String(params.professionId));
    if (params.expansionId != null) query.set("expansionId", String(params.expansionId));
    if (params.search) query.set("search", params.search);
    if (params.outputItemId != null) query.set("outputItemId", String(params.outputItemId));
    if (params.ingredientItemId != null) query.set("ingredientItemId", String(params.ingredientItemId));
    return query.toString();
}

export async function getRecipes(params: RecipeFilterParams): Promise<Page<RecipeSummary>> {
    return request<Page<RecipeSummary>>(`/recipes?${buildRecipeQuery(params)}`);
}

export async function getRecipe(id: number): Promise<RecipeDetail> {
    return request<RecipeDetail>(`/recipes/${id}`);
}

export async function getRecipeItemIds(): Promise<RecipeItemIdsResponse> {
    return request<RecipeItemIdsResponse>("/recipes/item-ids");
}

export async function duplicateRecipe(id: number): Promise<RecipeDetail> {
    return request<RecipeDetail>(`/recipes/${id}/duplicate`, { method: "POST" });
}

export async function deleteRecipe(id: number): Promise<void> {
    await request<void>(`/recipes/${id}`, { method: "DELETE" });
}

export async function createRecipe(payload: RecipeWritePayload): Promise<RecipeDetail> {
    return request<RecipeDetail>("/recipes", {
        method: "POST",
        body: JSON.stringify(payload),
    });
}

export async function updateRecipe(id: number, payload: RecipeWritePayload): Promise<RecipeDetail> {
    return request<RecipeDetail>(`/recipes/${id}`, {
        method: "PUT",
        body: JSON.stringify(payload),
    });
}

export async function refreshPricesForRecipes(recipeIds: number[]): Promise<RecipePriceRefreshResponse> {
    return request<RecipePriceRefreshResponse>("/craftingAH/fetch-for-recipes", {
        method: "POST",
        body: JSON.stringify({ recipeIds }),
    });
}

// ── Export ──────────────────────────────────────────────────────────────────────

/**
 * Triggers an Excel download of matching recipes. Handles the binary response
 * by creating a temporary <a> element to initiate the browser's file-save dialog.
 */
// ── Scraper (client-side, F3) ────────────────────────────────────────────────

export async function fetchWowheadProxy(url: string): Promise<string> {
    return request<string>(`/scraper/proxy?url=${encodeURIComponent(url)}`, {
        method: "GET",
        headers: { Accept: "text/html" },
    });
}

export async function importRecipes(commands: RecipeImportCommand[]): Promise<ImportResult> {
    return request<ImportResult>("/scraper/import", {
        method: "POST",
        body: JSON.stringify(commands),
    });
}

export async function getExistingSpellIds(expansionId?: number): Promise<number[]> {
    const params = expansionId != null ? `?expansionId=${expansionId}` : "";
    return request<number[]>(`/recipes/spell-ids${params}`);
}

// ── Characters (F1) ─────────────────────────────────────────────────────────

export async function getCharacters(): Promise<Character[]> {
    return request<Character[]>("/characters");
}

export async function createCharacter(req: CreateCharacterRequest): Promise<Character> {
    return request<Character>("/characters", {
        method: "POST",
        body: JSON.stringify(req),
    });
}

export async function updateCharacter(id: number, req: UpdateCharacterRequest): Promise<Character> {
    return request<Character>(`/characters/${id}`, {
        method: "PUT",
        body: JSON.stringify(req),
    });
}

export async function deleteCharacter(id: number): Promise<void> {
    await request<void>(`/characters/${id}`, { method: "DELETE" });
}

export async function refreshCharacterIcon(id: number): Promise<Character> {
    return request<Character>(`/characters/${id}/icon`, { method: "POST" });
}

// ── Character recipe assignments ────────────────────────────────────────────

export async function getCharacterRecipes(characterId: number): Promise<RecipeSummary[]> {
    return request<RecipeSummary[]>(`/characters/${characterId}/recipes`);
}

export async function assignRecipes(characterId: number, recipeIds: number[]): Promise<void> {
    await request<void>(`/characters/${characterId}/recipes`, {
        method: "POST",
        body: JSON.stringify({ recipeIds }),
    });
}

export async function unassignRecipe(characterId: number, recipeId: number): Promise<void> {
    await request<void>(`/characters/${characterId}/recipes/${recipeId}`, {
        method: "DELETE",
    });
}

// ── Dashboard (F2) ──────────────────────────────────────────────────────────

export async function getDashboardCrafts(params: {
    characterId?: number;
    professionId?: number;
    search?: string;
    sort?: string;
    direction?: string;
}): Promise<DashboardResponse> {
    const query = new URLSearchParams();
    if (params.characterId != null) query.set("characterId", String(params.characterId));
    if (params.professionId != null) query.set("professionId", String(params.professionId));
    if (params.search) query.set("search", params.search);
    if (params.sort) query.set("sort", params.sort);
    if (params.direction) query.set("direction", params.direction);
    return request<DashboardResponse>(`/dashboard/crafts?${query.toString()}`);
}

export async function exportRecipesExcel(params: RecipeFilterParams): Promise<void> {
    const token = getToken();
    const response = await fetch(`${BASE_URL}/export/recipes/excel?${buildRecipeQuery(params)}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
    });

    if (response.status === 401) {
        clearAuth();
        window.location.reload();
        throw new Error("Session expired — please log in again.");
    }
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `Export failed: ${response.status}`);
    }

    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    const disposition = response.headers.get("content-disposition");
    const match = disposition?.match(/filename="([^"]+)"/);
    a.download = match?.[1] ?? "recipes.xlsx";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

import { useCallback, useEffect, useMemo, useState } from "react";
import { archiveLogs, clearAuth, clearLogs, createItem, deleteItem, devLogin, exchangeDiscordCode, fetchCraftingAH, getItems, getProfessions, getStoredUser, getToken, setAuth, updateItem } from "./api";
import type { Item, Profession } from "./types";
import { AdminPage, SheetBuilder } from "./components/admin";
import { LoginPage } from "./components/auth";
import { CharactersPage } from "./components/characters";
import { DashboardPage } from "./components/dashboard";
import { CreateItemForm, DeleteItemForm, ItemList, UpdateItemForm } from "./components/items";
import { RecipesPage } from "./components/recipes";

export default function App() {
    // ── Auth state ──
    const [authed, setAuthed] = useState(() => !!getToken());
    const [user, setUser] = useState(getStoredUser);
    const [authError, setAuthError] = useState<string | null>(null);

    // Handle Discord OAuth callback (?code= in URL)
    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const code = params.get("code");
        if (!code) return;

        // Clean the URL immediately so a refresh doesn't re-send the code
        window.history.replaceState({}, "", window.location.pathname);

        exchangeDiscordCode(code, window.location.origin + "/")
            .then((auth) => {
                setAuth(auth);
                setAuthed(true);
                setUser({ discordUsername: auth.discordUsername, avatarUrl: auth.avatarUrl, role: auth.role });
            })
            .catch((err) => {
                setAuthError(err instanceof Error ? err.message : "Login failed.");
            });
    }, []);

    const handleLogout = () => {
        clearAuth();
        setAuthed(false);
        setUser(null);
    };

    const handleDevLogin = async () => {
        setAuthError(null);
        try {
            const auth = await devLogin();
            setAuth(auth);
            setAuthed(true);
            setUser({ discordUsername: auth.discordUsername, avatarUrl: auth.avatarUrl, role: auth.role });
        } catch (err) {
            setAuthError(err instanceof Error ? err.message : "Dev login failed.");
        }
    };

    // Show login page when not authenticated
    if (!authed) {
        return (
            <>
                <LoginPage onDevLogin={handleDevLogin} />
                {authError && <div className="login-error">{authError}</div>}
            </>
        );
    }

    // ── Authenticated app ──
    return <AuthenticatedApp user={user} onLogout={handleLogout} />;
}

function AuthenticatedApp({ user, onLogout }: { user: { discordUsername: string; avatarUrl: string | null; role: string | null } | null; onLogout: () => void }) {
    const [items, setItems] = useState<Item[]>([]);
    const [selectedItem, setSelectedItem] = useState<Item | null>(null);
    const [query, setQuery] = useState("");
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [logsMessage, setLogsMessage] = useState<string | null>(null);
    const [logsBusy, setLogsBusy] = useState(false);
    const [activePane, setActivePane] = useState<"create" | "update" | "delete">("create");
    const [ahMessage, setAhMessage] = useState<string | null>(null);
    const [ahError, setAhError] = useState<string | null>(null);
    const [ahBusy, setAhBusy] = useState(false);
    const [professions, setProfessions] = useState<Profession[]>([]);
    const [selectedProfessionIds, setSelectedProfessionIds] = useState<Set<number>>(new Set());
    const [showMissingIcons, setShowMissingIcons] = useState(false);
    const [showNoProfession, setShowNoProfession] = useState(false);
    const [showVendorItems, setShowVendorItems] = useState(false);
    const [activeTab, setActiveTab] = useState<"items" | "recipes" | "characters" | "dashboard" | "admin">("dashboard");

    const role = user?.role ?? null;
    // Frontend hides UI elements for UX only; backend enforces auth on every request.
    const isAdmin = role === "ADMIN" || role === "OWNER";

    const refreshItems = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await getItems();
            setItems(data);
            // Ensure the selectedItem remains valid after a refresh.
            // If there are no items, clear selection. If the previously
            // selected item was deleted, select the first available item.
            if (data.length === 0) {
                setSelectedItem(null);
            } else {
                if (!selectedItem) {
                    setSelectedItem(data[0]);
                } else {
                    const exists = data.some((it) => it.id === selectedItem.id);
                    if (!exists) {
                        setSelectedItem(data[0]);
                    }
                }
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to load items.");
        } finally {
            setLoading(false);
        }
    }, [selectedItem]);

    useEffect(() => {
        refreshItems();
    }, [refreshItems]);

    useEffect(() => {
        getProfessions()
            .then(setProfessions)
            .catch(() => setProfessions([]));
    }, []);

    const filteredItems = useMemo(() => {
        const lowered = query.toLowerCase();
        return items.filter((item) => {
            const matchesQuery = item.name.toLowerCase().includes(lowered) || String(item.id).includes(lowered);
            if (!matchesQuery) return false;
            if (showMissingIcons && item.iconUrl) return false;
            if (showNoProfession && item.profession != null) return false;
            if (showVendorItems && !item.vendorItem) return false;
            if (selectedProfessionIds.size === 0) return true;
            return item.profession != null && selectedProfessionIds.has(item.profession.id);
        });
    }, [items, query, selectedProfessionIds, showMissingIcons, showNoProfession, showVendorItems]);

    const toggleProfession = useCallback((id: number) => {
        setSelectedProfessionIds((prev) => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    }, []);

    const handleCreate = async (item: Item) => {
        await createItem(item);
        await refreshItems();
    };

    const handleUpdate = async (item: Item) => {
        await updateItem(item.id, item);
        await refreshItems();
    };

    const handleDelete = async (id: number) => {
        await deleteItem(id);
        await refreshItems();
    };

    const handleArchiveLogs = async () => {
        setLogsBusy(true);
        setLogsMessage(null);
        try {
            await archiveLogs();
            setLogsMessage("Logs archived.");
            setTimeout(() => setLogsMessage(null), 3000);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to archive logs.");
        } finally {
            setLogsBusy(false);
        }
    };

    const handleClearLogs = async () => {
        setLogsBusy(true);
        setLogsMessage(null);
        try {
            await clearLogs();
            setLogsMessage("Archives cleared.");
            setTimeout(() => setLogsMessage(null), 3000);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to clear logs.");
        } finally {
            setLogsBusy(false);
        }
    };

    const handleAhRefresh = async () => {
        setAhBusy(true);
        setAhMessage(null);
        setAhError(null);
        try {
            const response = await fetchCraftingAH();
            const message = typeof response === "string" && response.trim().length > 0
                ? response
                : "Refresh accepted by server.";
            setAhMessage(message);
            setTimeout(() => setAhMessage(null), 3000);
        } catch (err) {
            setAhError(err instanceof Error ? err.message : "Failed to start refresh.");
        } finally {
            setAhBusy(false);
        }
    };

    const canUseAddonDataSubmit = isAdmin;

    return (
        <div className={`app${activeTab === "dashboard" ? " app-dashboard" : ""}`}>
            {canUseAddonDataSubmit && <SheetBuilder />}
            <div className="header">
                <div>
                    <h1>Crafting Control Panel</h1>
                    <div className="muted">Items, Recipes &amp; Logs</div>
                </div>
                <div className="header-actions">
                    {user && (
                        <span className="user-badge">
                            {user.avatarUrl && <img src={user.avatarUrl} alt="" className="user-avatar" />}
                            <span className="muted">{user.discordUsername}</span>
                        </span>
                    )}
                    <button className="button secondary" type="button" onClick={onLogout}>
                        Logout
                    </button>
                    {isAdmin && activeTab === "items" && (
                        <>
                            <span className="separator" />
                            <button className="button secondary" type="button" onClick={refreshItems}>
                                Refresh
                            </button>
                        </>
                    )}
                </div>
            </div>

            {/* ── Top-level tab navigation ── */}
            <div className="app-tabs">
                {isAdmin && (
                    <button
                        type="button"
                        className={`app-tab${activeTab === "items" ? " active" : ""}`}
                        onClick={() => setActiveTab("items")}
                    >
                        Items
                    </button>
                )}
                {isAdmin && (
                    <button
                        type="button"
                        className={`app-tab${activeTab === "recipes" ? " active" : ""}`}
                        onClick={() => setActiveTab("recipes")}
                    >
                        Recipes
                    </button>
                )}
                <button
                    type="button"
                    className={`app-tab${activeTab === "characters" ? " active" : ""}`}
                    onClick={() => setActiveTab("characters")}
                >
                    Characters
                </button>
                <button
                    type="button"
                    className={`app-tab${activeTab === "dashboard" ? " active" : ""}`}
                    onClick={() => setActiveTab("dashboard")}
                >
                    Dashboard
                </button>
                {isAdmin && (
                    <button
                        type="button"
                        className={`app-tab${activeTab === "admin" ? " active" : ""}`}
                        onClick={() => setActiveTab("admin")}
                    >
                        Admin
                    </button>
                )}
            </div>

            {error && <div className="card">{error}</div>}

            {activeTab === "recipes" && (
                <div className="card" style={{ marginTop: 16 }}>
                    <RecipesPage professions={professions} role={role} />
                </div>
            )}

            {activeTab === "characters" && (
                <div className="card" style={{ marginTop: 16 }}>
                    <CharactersPage professions={professions} />
                </div>
            )}

            {activeTab === "dashboard" && (
                <div className="card" style={{ marginTop: 16 }}>
                    <DashboardPage professions={professions} role={role} />
                </div>
            )}

            {activeTab === "admin" && isAdmin && (
                <AdminPage
                    items={items}
                    professions={professions}
                    currentUserRole={role}
                    onAhRefresh={handleAhRefresh}
                    ahBusy={ahBusy}
                    ahMessage={ahMessage}
                    ahError={ahError}
                    onArchiveLogs={handleArchiveLogs}
                    onClearLogs={handleClearLogs}
                    logsMessage={logsMessage}
                    logsBusy={logsBusy}
                />
            )}

            {activeTab === "items" && (
            <div className="grid">
                <div className="card">
                    <div className="card-header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 6, flexWrap: "wrap" }}>
                        <h3>Items</h3>
                        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                            <button
                                type="button"
                                className={`profession-chip${showMissingIcons ? " active" : ""}`}
                                onClick={() => setShowMissingIcons((v) => !v)}
                            >
                                🖼 Missing icons
                            </button>
                            <button
                                type="button"
                                className={`profession-chip${showNoProfession ? " active" : ""}`}
                                onClick={() => setShowNoProfession((v) => !v)}
                            >
                                📋 No category
                            </button>
                            <button
                                type="button"
                                className={`profession-chip${showVendorItems ? " active" : ""}`}
                                onClick={() => setShowVendorItems((v) => !v)}
                            >
                                💰 Vendor items
                            </button>
                        </div>
                    </div>
                    <input
                        className="input"
                        placeholder="Search by id or name"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                    />
                    <div className="profession-filter">
                        {professions.map((p) => (
                            <button
                                key={p.id}
                                type="button"
                                className={`profession-chip${selectedProfessionIds.has(p.id) ? " active" : ""}`}
                                onClick={() => toggleProfession(p.id)}
                            >
                                {p.name}
                            </button>
                        ))}
                        {(selectedProfessionIds.size > 0 || showMissingIcons || showNoProfession || showVendorItems) && (
                            <button
                                type="button"
                                className="profession-chip clear"
                                onClick={() => { setSelectedProfessionIds(new Set()); setShowMissingIcons(false); setShowNoProfession(false); setShowVendorItems(false); }}
                            >
                                ✕ Clear
                            </button>
                        )}
                    </div>
                    {loading ? <div className="muted">Loading...</div> : null}
                    <ItemList items={filteredItems} selectedItem={selectedItem} onSelect={setSelectedItem} />
                </div>

                <div className="grid" style={{ gridTemplateColumns: "1fr" }}>
                    <div className="card">
                        <div className="tabs">
                            <button
                                type="button"
                                className={`tab ${activePane === "create" ? "active" : ""}`}
                                onClick={() => setActivePane("create")}
                            >
                                Create
                            </button>
                            <button
                                type="button"
                                className={`tab ${activePane === "update" ? "active" : ""}`}
                                onClick={() => setActivePane("update")}
                            >
                                Update
                            </button>
                            <button
                                type="button"
                                className={`tab ${activePane === "delete" ? "active" : ""}`}
                                onClick={() => setActivePane("delete")}
                            >
                                Delete
                            </button>
                        </div>
                        <div className="tab-body">
                            {activePane === "create" ? <CreateItemForm onCreate={handleCreate} professions={professions} /> : null}
                            {activePane === "update" ? (
                                <UpdateItemForm
                                    items={items}
                                    selectedItem={selectedItem}
                                    onSelect={setSelectedItem}
                                    onUpdate={handleUpdate}
                                    professions={professions}
                                />
                            ) : null}
                            {activePane === "delete" ? (
                                <DeleteItemForm
                                    items={items}
                                    selectedItem={selectedItem}
                                    onSelect={setSelectedItem}
                                    onDelete={handleDelete}
                                />
                            ) : null}
                        </div>
                    </div>
                </div>
            </div>
            )}
        </div>
    );
}

import { useCallback, useEffect, useMemo, useState } from "react";
import { archiveLogs, clearLogs, createItem, deleteItem, fetchCraftingAH, getItems, getProfessions, updateItem } from "./api";
import type { Item, Profession } from "./types";
import CreateItemForm from "./components/CreateItemForm";
import UpdateItemForm from "./components/UpdateItemForm";
import DeleteItemForm from "./components/DeleteItemForm";
import ItemList from "./components/ItemList";
import LogsPanel from "./components/LogsPanel";
import EndpointSettings from "./components/EndpointSettings";

export default function App() {
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
            if (selectedProfessionIds.size === 0) return true;
            return item.profession != null && selectedProfessionIds.has(item.profession.id);
        });
    }, [items, query, selectedProfessionIds]);

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

    return (
        <div className="app">
            <EndpointSettings />
            <div className="header">
                <div>
                    <h1>Crafting Control Panel</h1>
                    <div className="muted">Using /items and /logs endpoints</div>
                </div>
                <div className="header-actions">
                    <button className="button secondary" type="button" onClick={refreshItems}>
                        Refresh
                    </button>
                    <span className="separator" />
                    <button className="button" type="button" onClick={handleAhRefresh} disabled={ahBusy}>
                        {ahBusy ? "Refreshing..." : "AH Refresh"}
                    </button>
                    {(ahMessage || ahError) && (
                        <span className={`status-inline ${ahError ? "status-error" : "status-success"}`}>
                            {ahError ?? ahMessage}
                        </span>
                    )}
                </div>
            </div>

            {error && <div className="card">{error}</div>}

            <div className="grid">
                <div className="card">
                    <h3>Items</h3>
                    <input
                        className="input"
                        placeholder="Search by id or name"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                    />
                    {professions.length > 0 && (
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
                            {selectedProfessionIds.size > 0 && (
                                <button
                                    type="button"
                                    className="profession-chip clear"
                                    onClick={() => setSelectedProfessionIds(new Set())}
                                >
                                    ✕ Clear
                                </button>
                            )}
                        </div>
                    )}
                    {loading ? <div className="muted">Loading...</div> : null}
                    <ItemList items={filteredItems} />
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
                    <LogsPanel onArchive={handleArchiveLogs} onClear={handleClearLogs} message={logsMessage} busy={logsBusy} />
                </div>
            </div>
        </div>
    );
}

import { useEffect, useMemo, useState } from "react";
import { fetchWowheadProxy, getExistingSpellIds, getItems, importRecipes } from "../api";
import type { Expansion, ImportResult, Item, Profession, RecipeImportCommand, ScrapedRecipe } from "../types";
import { parseRecipesFromHtml } from "../wowheadParser";
import ScrapedRecipeTable from "./ScrapedRecipeTable";

/** Profession slugs supported by Wowhead listing URLs */
const SCRAPER_SUPPORTED = new Set([
    "alchemy", "blacksmithing", "enchanting", "engineering",
    "inscription", "jewelcrafting", "leatherworking", "tailoring",
]);

const PROFESSION_SUFFIX: Record<string, string> = {
    alchemy: "recipes",
    blacksmithing: "plans",
    enchanting: "formulas",
    engineering: "schematics",
    inscription: "techniques",
    jewelcrafting: "designs",
    leatherworking: "patterns",
    tailoring: "patterns",
};

function slugify(name: string): string {
    return name.trim().toLowerCase().replace(/\s+/g, "-");
}

type Props = {
    professions: Profession[];
    expansions: Expansion[];
    onScrapeComplete: () => void;
};

export default function ScraperPanel({ professions, expansions, onScrapeComplete }: Props) {
    // ── Collapsible state (F4) ──
    const [collapsed, setCollapsed] = useState(() => {
        return localStorage.getItem("scraperCollapsed") !== "false";
    });

    const toggleCollapsed = () => {
        setCollapsed((prev) => {
            const next = !prev;
            localStorage.setItem("scraperCollapsed", String(next));
            return next;
        });
    };

    // ── Item lookup for U1 ──
    const [itemMap, setItemMap] = useState<Map<number, Item>>(new Map());

    useEffect(() => {
        getItems()
            .then((items) => setItemMap(new Map(items.map((it) => [it.id, it]))))
            .catch(() => { /* non-critical — table falls back to raw IDs */ });
    }, []);

    // ── Scraper state ──
    const supported = useMemo(
        () => professions.filter((p) => SCRAPER_SUPPORTED.has(slugify(p.name))),
        [professions],
    );

    const [professionSlug, setProfessionSlug] = useState("");
    const [expansionSlug, setExpansionSlug] = useState("");
    const [selectedExpansionId, setSelectedExpansionId] = useState<number | null>(null);
    const [fetching, setFetching] = useState(false);
    const [importing, setImporting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [recipes, setRecipes] = useState<ScrapedRecipe[]>([]);
    const [importResult, setImportResult] = useState<ImportResult | null>(null);

    // Set default profession once the list loads
    useEffect(() => {
        if (supported.length > 0 && !professionSlug) {
            setProfessionSlug(slugify(supported[0].name));
        }
    }, [supported, professionSlug]);

    // Set default expansion once the list loads
    useEffect(() => {
        if (expansions.length > 0 && !expansionSlug) {
            setExpansionSlug(expansions[0].slug);
            setSelectedExpansionId(expansions[0].id);
        }
    }, [expansions, expansionSlug]);

    const listingUrl = useMemo(() => {
        if (!professionSlug || !expansionSlug) return "";
        const suffix = PROFESSION_SUFFIX[professionSlug] ?? "recipes";
        return `https://www.wowhead.com/spells/professions/${professionSlug}/${expansionSlug}-${suffix}`;
    }, [professionSlug, expansionSlug]);

    const selectedProfessionId = useMemo(() => {
        const prof = professions.find((p) => slugify(p.name) === professionSlug);
        return prof?.id ?? null;
    }, [professions, professionSlug]);

    const handleExpansionChange = (slug: string) => {
        setExpansionSlug(slug);
        const exp = expansions.find((e) => e.slug === slug);
        setSelectedExpansionId(exp?.id ?? null);
    };

    const handleFetch = async () => {
        if (!listingUrl) return;
        setFetching(true);
        setError(null);
        setRecipes([]);
        setImportResult(null);

        try {
            const html = await fetchWowheadProxy(listingUrl);
            const parsed = parseRecipesFromHtml(html);

            // Fetch existing spell IDs to determine "new" vs "exists" status
            const existingIds = new Set(await getExistingSpellIds(selectedExpansionId ?? undefined));

            const scraped: ScrapedRecipe[] = parsed.map((r) => ({
                ...r,
                selected: !existingIds.has(r.spellId), // auto-select new ones
                status: existingIds.has(r.spellId) ? "exists" : "new",
            }));

            setRecipes(scraped);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to fetch from Wowhead.");
        } finally {
            setFetching(false);
        }
    };

    const handleImport = async () => {
        if (selectedProfessionId == null || selectedExpansionId == null) return;

        const selected = recipes.filter((r) => r.selected);
        if (selected.length === 0) return;

        setImporting(true);
        setError(null);
        setImportResult(null);

        try {
            const commands: RecipeImportCommand[] = selected.map((r) => ({
                wowheadSpellId: r.spellId,
                recipeName: r.name,
                outputItemId: r.outputItemId,
                outputQuantity: r.outputQuantity,
                professionId: selectedProfessionId,
                expansionId: selectedExpansionId,
                ingredients: r.reagents,
            }));

            const result = await importRecipes(commands);
            setImportResult(result);
            setRecipes([]);
            onScrapeComplete();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Import failed.");
        } finally {
            setImporting(false);
        }
    };

    const selectedCount = recipes.filter((r) => r.selected).length;

    return (
        <div className="scraper-panel">
            {/* ── Collapsible header ── */}
            <button type="button" className="scraper-collapse-header" onClick={toggleCollapsed}>
                <span className="scraper-panel-title">🔧 Scraper</span>
                <span className={`chev${collapsed ? "" : " rotated"}`}>▶</span>
            </button>

            {/* ── Collapsible body ── */}
            <div className={`scraper-body${collapsed ? " collapsed" : ""}`}>
                {/* ── Trigger section ── */}
                <div className="scraper-controls" style={{ marginTop: 10 }}>
                    <select
                        className="input"
                        value={professionSlug}
                        onChange={(e) => setProfessionSlug(e.target.value)}
                        disabled={fetching}
                        aria-label="Profession"
                    >
                        {supported.map((p) => (
                            <option key={p.id} value={slugify(p.name)}>
                                {p.name}
                            </option>
                        ))}
                    </select>
                    <select
                        className="input"
                        value={expansionSlug}
                        onChange={(e) => handleExpansionChange(e.target.value)}
                        disabled={fetching}
                        aria-label="Expansion"
                    >
                        {expansions.map((exp) => (
                            <option key={exp.id} value={exp.slug}>
                                {exp.name}
                            </option>
                        ))}
                    </select>
                    <button
                        type="button"
                        className="button"
                        onClick={() => void handleFetch()}
                        disabled={fetching || !listingUrl}
                    >
                        {fetching ? "Fetching…" : "🌐 Fetch from Wowhead"}
                    </button>
                </div>

                {listingUrl && (
                    <div className="muted" style={{ fontSize: 11, marginTop: 4, wordBreak: "break-all" }}>
                        {listingUrl}
                    </div>
                )}

                {error && <div className="scraper-error">{error}</div>}

                {/* ── Results table ── */}
                {recipes.length > 0 && (
                    <>
                        <ScrapedRecipeTable recipes={recipes} onChange={setRecipes} itemMap={itemMap} />
                        <div style={{ display: "flex", gap: 8, alignItems: "center", marginTop: 8 }}>
                            <button
                                type="button"
                                className="button primary"
                                onClick={() => void handleImport()}
                                disabled={importing || selectedCount === 0}
                            >
                                {importing ? "Importing…" : `Import Selected (${selectedCount})`}
                            </button>
                        </div>
                    </>
                )}

                {/* ── Import result ── */}
                {importResult && (
                    <div className="scraper-result">
                        <div className="scraper-stats">
                            <span className="scraper-stat stat-added">+{importResult.added} added</span>
                            <span className="scraper-stat stat-updated">↻ {importResult.updated} updated</span>
                            <span className="scraper-stat stat-skipped">— {importResult.skipped} skipped</span>
                        </div>
                        {importResult.errors.length > 0 && (
                            <details className="scraper-errors">
                                <summary>
                                    ⚠️ {importResult.errors.length} error{importResult.errors.length !== 1 ? "s" : ""}
                                </summary>
                                <ul>
                                    {importResult.errors.map((msg, i) => (
                                        <li key={i}>{msg}</li>
                                    ))}
                                </ul>
                            </details>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

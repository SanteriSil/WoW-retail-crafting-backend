import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getScraperStatus, triggerScrape } from "../api";
import type { Expansion, Profession, ScraperStatus } from "../types";

/** Profession slugs supported by WowheadScraper.resolveProfessionSuffix() */
const SCRAPER_SUPPORTED = new Set([
    "alchemy",
    "blacksmithing",
    "enchanting",
    "engineering",
    "inscription",
    "jewelcrafting",
    "leatherworking",
    "tailoring",
]);

function slugify(name: string): string {
    return name.trim().toLowerCase().replace(/\s+/g, "-");
}

type Props = {
    professions: Profession[];
    expansions: Expansion[];
    onScrapeComplete: () => void;
};

export default function ScraperPanel({ professions, expansions, onScrapeComplete }: Props) {
    const supported = useMemo(
        () => professions.filter((p) => SCRAPER_SUPPORTED.has(slugify(p.name))),
        [professions],
    );

    const [professionSlug, setProfessionSlug] = useState("");
    const [expansionSlug, setExpansionSlug] = useState("");
    const [status, setStatus] = useState<ScraperStatus | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [triggering, setTriggering] = useState(false);

    // Stable ref for the callback to avoid re-creating the polling interval
    const onCompleteRef = useRef(onScrapeComplete);
    useEffect(() => { onCompleteRef.current = onScrapeComplete; }, [onScrapeComplete]);

    // Track previous running state to detect when a scrape finishes
    const wasRunning = useRef(false);
    const pollInterval = useRef<ReturnType<typeof setInterval> | null>(null);

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
        }
    }, [expansions, expansionSlug]);

    const fetchStatus = useCallback(async () => {
        try {
            const s = await getScraperStatus();
            const justFinished = wasRunning.current && !s.running;
            wasRunning.current = s.running;
            setStatus(s);
            if (justFinished) {
                onCompleteRef.current();
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to get scraper status.");
        }
    }, []);

    // Fetch status on mount
    useEffect(() => {
        void fetchStatus();
    }, [fetchStatus]);

    // Poll every 2 s while the scraper is running; clear interval when it stops
    useEffect(() => {
        if (status?.running) {
            pollInterval.current = setInterval(() => void fetchStatus(), 2000);
        }
        return () => {
            if (pollInterval.current) {
                clearInterval(pollInterval.current);
                pollInterval.current = null;
            }
        };
    }, [status?.running, fetchStatus]);

    const handleTrigger = async () => {
        if (!professionSlug || !expansionSlug) return;
        setError(null);
        setTriggering(true);
        try {
            await triggerScrape(professionSlug, expansionSlug);
            wasRunning.current = true;
            await fetchStatus();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to start scraper.");
        } finally {
            setTriggering(false);
        }
    };

    const running = status?.running ?? false;
    const result = status?.lastResult ?? null;

    return (
        <div className="scraper-panel">
            <div className="scraper-panel-header">
                <span className="scraper-panel-title">🕷 Wowhead Scraper</span>
                <div className="scraper-controls">
                    <select
                        className="input"
                        value={professionSlug}
                        onChange={(e) => setProfessionSlug(e.target.value)}
                        disabled={running}
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
                        onChange={(e) => setExpansionSlug(e.target.value)}
                        disabled={running}
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
                        onClick={() => void handleTrigger()}
                        disabled={running || triggering || !professionSlug || !expansionSlug}
                    >
                        {running ? "Running…" : "▶ Run Scrape"}
                    </button>
                </div>
            </div>

            {running && (
                <div className="scraper-running">
                    <span className="scraper-spinner" aria-hidden="true" />
                    Scraping{" "}
                    <strong>{result?.professionSlug ?? professionSlug}</strong>
                    {" / "}
                    <strong>{result?.expansionSlug ?? expansionSlug}</strong>
                    …
                </div>
            )}

            {error && <div className="scraper-error">{error}</div>}

            {!running && result && (
                <div className="scraper-result">
                    <span className="scraper-result-label">
                        Last run: <strong>{result.professionSlug}</strong> / <strong>{result.expansionSlug}</strong>
                    </span>
                    <div className="scraper-stats">
                        <span className="scraper-stat stat-added">+{result.added} added</span>
                        <span className="scraper-stat stat-updated">↻ {result.updated} updated</span>
                        <span className="scraper-stat stat-skipped">— {result.skipped} skipped</span>
                        <span className="scraper-stat">
                            📄 {result.listingPagesVisited} pages · {result.listingEntriesFound} found
                        </span>
                        {result.autoCreatedItemIds.length > 0 && (
                            <span className="scraper-stat">
                                🧱 {result.autoCreatedItemIds.length} item stub{result.autoCreatedItemIds.length !== 1 ? "s" : ""} created
                            </span>
                        )}
                    </div>
                    {result.errors.length > 0 && (
                        <details className="scraper-errors">
                            <summary>
                                ⚠️ {result.errors.length} error{result.errors.length !== 1 ? "s" : ""}
                            </summary>
                            <ul>
                                {result.errors.map((msg, i) => (
                                    <li key={i}>{msg}</li>
                                ))}
                            </ul>
                        </details>
                    )}
                </div>
            )}
        </div>
    );
}

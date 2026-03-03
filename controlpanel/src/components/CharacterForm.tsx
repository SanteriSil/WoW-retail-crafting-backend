import { useEffect, useState } from "react";
import type { Region } from "../data/realms";
import type { Character, CreateCharacterRequest, Profession } from "../types";
import RealmAutocomplete from "./RealmAutocomplete";

type Props = {
    professions: Profession[];
    character: Character | null;
    onSubmit: (req: CreateCharacterRequest) => Promise<void>;
    onCancel: () => void;
};

interface ProfSlot {
    professionId: number | "";
    multicraftPercent: number;
    resourcefulnessPercent: number;
}

const emptySlot = (): ProfSlot => ({ professionId: "", multicraftPercent: 0, resourcefulnessPercent: 0 });

export default function CharacterForm({ professions, character, onSubmit, onCancel }: Props) {
    const [name, setName] = useState("");
    const [realm, setRealm] = useState("");
    const [region, setRegion] = useState<Region>("EU");
    const [prof1, setProf1] = useState<ProfSlot>(emptySlot());
    const [prof2, setProf2] = useState<ProfSlot>(emptySlot());
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Populate form when a character is selected
    useEffect(() => {
        if (character) {
            setName(character.name);
            setRealm(character.realm);
            const p1 = character.professions[0];
            const p2 = character.professions[1];
            setProf1(p1 ? { professionId: p1.professionId, multicraftPercent: p1.multicraftPercent, resourcefulnessPercent: p1.resourcefulnessPercent } : emptySlot());
            setProf2(p2 ? { professionId: p2.professionId, multicraftPercent: p2.multicraftPercent, resourcefulnessPercent: p2.resourcefulnessPercent } : emptySlot());
        } else {
            setName("");
            setRealm("");
            setProf1(emptySlot());
            setProf2(emptySlot());
        }
        setError(null);
    }, [character]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);

        if (!name.trim() || !realm.trim()) {
            setError("Name and realm are required.");
            return;
        }

        const profs = [prof1, prof2]
            .filter((p) => p.professionId !== "")
            .map((p) => ({
                professionId: p.professionId as number,
                multicraftPercent: p.multicraftPercent,
                resourcefulnessPercent: p.resourcefulnessPercent,
            }));

        setBusy(true);
        try {
            await onSubmit({ name: name.trim(), realm: realm.trim(), professions: profs });
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed.");
        } finally {
            setBusy(false);
        }
    };

    const renderProfSlot = (slot: ProfSlot, setSlot: (s: ProfSlot) => void, label: string) => (
        <div className="character-prof-slot">
            <div className="field">
                <label className="label">{label}</label>
                <select
                    className="input"
                    value={slot.professionId}
                    onChange={(e) => setSlot({ ...slot, professionId: e.target.value ? Number(e.target.value) : "" })}
                >
                    <option value="">— None —</option>
                    {professions.map((p) => (
                        <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                </select>
            </div>
            {slot.professionId !== "" && (
                <div className="character-stat-row">
                    <div className="field" style={{ marginBottom: 0 }}>
                        <label className="label">Multicraft %</label>
                        <input
                            type="number"
                            className="input"
                            value={slot.multicraftPercent}
                            min={0}
                            max={100}
                            step={0.1}
                            onChange={(e) => setSlot({ ...slot, multicraftPercent: parseFloat(e.target.value) || 0 })}
                        />
                    </div>
                    <div className="field" style={{ marginBottom: 0 }}>
                        <label className="label">Resourcefulness %</label>
                        <input
                            type="number"
                            className="input"
                            value={slot.resourcefulnessPercent}
                            min={0}
                            max={100}
                            step={0.1}
                            onChange={(e) => setSlot({ ...slot, resourcefulnessPercent: parseFloat(e.target.value) || 0 })}
                        />
                    </div>
                </div>
            )}
        </div>
    );

    return (
        <form className="character-form card" onSubmit={(e) => void handleSubmit(e)}>
            <h3 style={{ margin: "0 0 12px" }}>{character ? "Edit Character" : "Create Character"}</h3>

            {error && <div className="error" style={{ marginBottom: 8 }}>{error}</div>}

            <div className="field">
                <label className="label">Name</label>
                <input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="Character name" />
            </div>
            <div className="field">
                <label className="label">Realm</label>
                <RealmAutocomplete
                    value={realm}
                    onChange={setRealm}
                    region={region}
                    onRegionChange={setRegion}
                />
            </div>

            {renderProfSlot(prof1, setProf1, "Profession 1")}
            {renderProfSlot(prof2, setProf2, "Profession 2")}

            <div className="form-actions">
                <button type="submit" className="button" disabled={busy}>
                    {busy ? "Saving…" : character ? "Update" : "Create"}
                </button>
                {character && (
                    <button type="button" className="button secondary" onClick={onCancel}>
                        Cancel
                    </button>
                )}
            </div>
        </form>
    );
}

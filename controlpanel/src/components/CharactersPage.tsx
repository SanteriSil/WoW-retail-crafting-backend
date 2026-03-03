import { useCallback, useEffect, useState } from "react";
import {
    createCharacter,
    deleteCharacter,
    getCharacters,
    refreshCharacterIcon,
    updateCharacter,
} from "../api";
import type { Character, CreateCharacterRequest, Profession, UpdateCharacterRequest } from "../types";
import CharacterForm from "./CharacterForm";
import CharacterList from "./CharacterList";
import CharacterRecipeAssignment from "./CharacterRecipeAssignment";

type Props = {
    professions: Profession[];
};

export default function CharactersPage({ professions }: Props) {
    const [characters, setCharacters] = useState<Character[]>([]);
    const [selected, setSelected] = useState<Character | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [status, setStatus] = useState<{ msg: string; ok: boolean } | null>(null);

    const refresh = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await getCharacters();
            setCharacters(data);
        } catch (err) {
            setError(err instanceof Error ? err.message : "Failed to load characters.");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void refresh();
    }, [refresh]);

    const handleCreate = async (req: CreateCharacterRequest) => {
        try {
            await createCharacter(req);
            setStatus({ msg: "Character created.", ok: true });
            await refresh();
            setSelected(null);
        } catch (err) {
            setStatus({ msg: err instanceof Error ? err.message : "Create failed.", ok: false });
        } finally {
            setTimeout(() => setStatus(null), 3000);
        }
    };

    const handleUpdate = async (id: number, req: UpdateCharacterRequest) => {
        try {
            const updated = await updateCharacter(id, req);
            setStatus({ msg: "Character updated.", ok: true });
            await refresh();
            setSelected(updated);
        } catch (err) {
            setStatus({ msg: err instanceof Error ? err.message : "Update failed.", ok: false });
        } finally {
            setTimeout(() => setStatus(null), 3000);
        }
    };

    const handleDelete = async (id: number) => {
        if (!window.confirm("Delete this character? All assignments will be removed.")) return;
        try {
            await deleteCharacter(id);
            setStatus({ msg: "Character deleted.", ok: true });
            setSelected(null);
            await refresh();
        } catch (err) {
            setStatus({ msg: err instanceof Error ? err.message : "Delete failed.", ok: false });
        } finally {
            setTimeout(() => setStatus(null), 3000);
        }
    };

    const handleRefreshIcon = async (id: number) => {
        try {
            const updated = await refreshCharacterIcon(id);
            setStatus({ msg: "Icon refreshed.", ok: true });
            await refresh();
            setSelected(updated);
        } catch (err) {
            setStatus({ msg: err instanceof Error ? err.message : "Icon refresh failed.", ok: false });
        } finally {
            setTimeout(() => setStatus(null), 3000);
        }
    };

    return (
        <div className="characters-page">
            <div className="recipes-page-header">
                <h2 style={{ margin: 0 }}>Characters</h2>
                {status && (
                    <span className={`status-inline ${status.ok ? "status-success" : "status-error"}`}>
                        {status.msg}
                    </span>
                )}
            </div>

            {error && <div className="error">{error}</div>}

            <div className="characters-grid">
                <div>
                    {loading && <div className="muted">Loading…</div>}
                    <CharacterList
                        characters={characters}
                        selected={selected}
                        onSelect={setSelected}
                        onDelete={handleDelete}
                        onRefreshIcon={handleRefreshIcon}
                    />
                </div>
                <div>
                    <CharacterForm
                        professions={professions}
                        character={selected}
                        onSubmit={(req) => {
                            if (selected) {
                                return handleUpdate(selected.id, req);
                            }
                            return handleCreate(req);
                        }}
                        onCancel={() => setSelected(null)}
                    />
                    {selected && (
                        <CharacterRecipeAssignment
                            characterId={selected.id}
                            professions={professions}
                            characterProfessionIds={selected.professions.map((p) => p.professionId)}
                            onAssignmentChange={() => void refresh()}
                        />
                    )}
                </div>
            </div>
        </div>
    );
}

/*
 * Panel administratora dla rozgrywek z adresu (?t=<slug>): druzyny, mecze i wyniki.
 * Nowe rozgrywki zaklada sie z kafelka "+" na stronie glownej ParlayHub (landing.js).
 * Ladowany PRZED app.js - App odwoluje sie do AdminPanel po nazwie.
 *
 * Wszystkie zapisy ida na /api/admin/**, gdzie serwer i tak sprawdza uprawnienia;
 * flaga admina w przegladarce decyduje wylacznie o pokazaniu zakladki.
 */

/** Wyciaga komunikat bledu z odpowiedzi (Spring zwraca go w polu "message"). */
async function adminError(res, fallback) {
    try {
        const body = await res.json();
        return body.message || body.error || fallback;
    } catch (_) {
        return fallback;
    }
}

/** ISO UTC -> wartosc dla <input type="datetime-local"> w czasie lokalnym przegladarki. */
function toLocalInput(isoUtc) {
    if (!isoUtc) return "";
    const d = new Date(isoUtc);
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Wartosc z <input type="datetime-local"> -> ISO UTC dla API. */
function toIsoUtc(local) {
    if (!local) return "";
    return new Date(local).toISOString();
}

function AdminNotice({ error, info }) {
    if (!error && !info) return null;
    return <div className={"admin-notice" + (error ? " admin-notice-error" : "")}>{error || info}</div>;
}

// ---- Druzyny ----
// Tylko dla klubow (CLUB) - reprezentacje (NATIONAL) nie maja tu osobnego kroku,
// bo dodaja sie same przy zakladaniu meczu (patrz MatchForm i globalny CountryCatalog).

function AdminTeams({ tournament, teams, onChanged, onError, onInfo }) {
    const [code, setCode] = useState("");
    const [name, setName] = useState("");
    const [crestUrl, setCrestUrl] = useState("");
    const [busy, setBusy] = useState(false);

    async function add(e) {
        e.preventDefault();
        setBusy(true);
        const res = await api(`${API}/admin/tournaments/${tournament.id}/teams`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ code, name, crestUrl }),
        });
        setBusy(false);
        if (!res.ok) return onError(await adminError(res, "Nie udało się dodać drużyny"));
        setCode(""); setName(""); setCrestUrl("");
        onInfo(`Dodano drużynę ${name}`);
        onChanged();
    }

    async function remove(team) {
        const res = await api(`${API}/admin/teams/${team.id}`, { method: "DELETE" });
        if (!res.ok) return onError(await adminError(res, "Nie udało się usunąć drużyny"));
        onInfo(`Usunięto drużynę ${team.name}`);
        onChanged();
    }

    return (
        <div className="admin-section">
            <form className="admin-form" onSubmit={add}>
                <div className="admin-form-row">
                    <label>
                        Nazwa
                        <input value={name} placeholder="np. Real Madryt"
                               onChange={(e) => setName(e.target.value)} />
                    </label>
                    <label>
                        Kod
                        <input value={code} placeholder="np. real"
                               onChange={(e) => setCode(e.target.value)} />
                    </label>
                    <label className="admin-grow">
                        Adres herbu
                        <input value={crestUrl} placeholder="https://…/herb.png"
                               onChange={(e) => setCrestUrl(e.target.value)} />
                    </label>
                    <button className="admin-btn admin-btn-primary" disabled={busy}>Dodaj</button>
                </div>
            </form>

            {teams.length === 0 ? (
                <p className="admin-empty">Brak drużyn. Dodaj je, zanim zaczniesz zakładać mecze.</p>
            ) : (
                <div className="admin-team-grid">
                    {teams.map((t) => (
                        <div className="admin-team" key={t.id}>
                            {t.crestUrl
                                ? <img className="admin-crest" src={t.crestUrl} alt="" loading="lazy" />
                                : <img className="admin-crest" src={flagUrl(t.code)} alt="" loading="lazy" />}
                            <span className="admin-team-name">{t.name}</span>
                            <span className="admin-team-code">{t.code}</span>
                            <button className="admin-x" title="Usuń drużynę"
                                    onClick={() => remove(t)}>×</button>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

// ---- Mecze ----

/** Dopasowuje wpisana nazwe do pozycji slownika (dokladnie, bez rozrozniania wielkosci liter). */
function findCountry(countries, typedName) {
    const q = typedName.trim().toLowerCase();
    if (!q) return null;
    return countries.find((c) => c.name.toLowerCase() === q) || null;
}

/** Pole "wpisz kraj" z podpowiedziami z globalnego slownika - do wyboru gospodarza/gościa. */
function CountryField({ label, listId, countries, value, onChange }) {
    return (
        <label>
            {label}
            <input value={value} placeholder="— nieznana —" list={listId}
                   onChange={(e) => onChange(e.target.value)} />
            <datalist id={listId}>
                {countries.map((c) => <option key={c.code} value={c.name} />)}
            </datalist>
        </label>
    );
}

function MatchForm({ tournament, teams, onCreated, onError }) {
    const [stage, setStage] = useState("group"); // "group" | "knockout"
    const [groupName, setGroupName] = useState("A");
    const [roundName, setRoundName] = useState(RUNDY_PUCHAROWE[0]);
    const [kickoff, setKickoff] = useState("");
    const [team1, setTeam1] = useState("");
    const [team2, setTeam2] = useState("");
    const [countries, setCountries] = useState([]);
    const [busy, setBusy] = useState(false);
    const clubs = tournament.teamKind === "CLUB";

    // Reprezentacje wybiera sie z globalnego slownika krajow wprost w formularzu meczu -
    // druzyna dopisuje sie do rozgrywek sama, bez osobnego kroku "dodaj druzyne".
    useEffect(() => {
        if (clubs) return;
        api(`${API}/admin/countries`).then((res) => res.ok && res.json()).then((list) => list && setCountries(list));
    }, [clubs]);

    // Rejestruje w rozgrywkach kraj wybrany w formularzu, jesli jeszcze go tam nie ma.
    // 400 (kod juz istnieje) traktujemy jako sukces - inny mecz go juz dopisal.
    async function ensureTeam(country) {
        if (!country || teams.some((t) => t.code === country.code)) return true;
        const res = await api(`${API}/admin/tournaments/${tournament.id}/teams`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ code: country.code, name: country.name, crestUrl: null }),
        });
        return res.ok || res.status === 400;
    }

    async function submit(e) {
        e.preventDefault();

        let team1Code = team1;
        let team2Code = team2;
        if (!clubs) {
            const c1 = findCountry(countries, team1);
            const c2 = findCountry(countries, team2);
            if ((team1.trim() && !c1) || (team2.trim() && !c2)) {
                return onError("Wybierz kraj z podpowiedzi (zacznij pisać nazwę)");
            }
            setBusy(true);
            if (!(await ensureTeam(c1)) || !(await ensureTeam(c2))) {
                setBusy(false);
                return onError("Nie udało się zarejestrować drużyny w rozgrywkach");
            }
            team1Code = c1 ? c1.code : "";
            team2Code = c2 ? c2.code : "";
        } else {
            setBusy(true);
        }

        const res = await api(`${API}/admin/tournaments/${tournament.id}/matches`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                groupName: stage === "group" ? groupName : null,
                roundName: stage === "knockout" ? roundName : null,
                kickoffUtc: toIsoUtc(kickoff),
                team1Code: team1Code || null,
                team2Code: team2Code || null,
            }),
        });
        setBusy(false);
        if (!res.ok) return onError(await adminError(res, "Nie udało się dodać meczu"));
        setTeam1(""); setTeam2("");
        onCreated();
    }

    return (
        <form className="admin-form" onSubmit={submit}>
            <div className="admin-form-row">
                <label>
                    Faza
                    <select value={stage} onChange={(e) => setStage(e.target.value)}>
                        <option value="group">Grupowa</option>
                        <option value="knockout">Pucharowa</option>
                    </select>
                </label>
                {stage === "group" ? (
                    <label>
                        Grupa
                        <input value={groupName} maxLength={3}
                               onChange={(e) => setGroupName(e.target.value.toUpperCase())} />
                    </label>
                ) : (
                    <label>
                        Runda
                        <select value={roundName} onChange={(e) => setRoundName(e.target.value)}>
                            {RUNDY_PUCHAROWE.map((r) => <option key={r} value={r}>{r}</option>)}
                        </select>
                    </label>
                )}
                <label>
                    Początek meczu
                    <input type="datetime-local" value={kickoff}
                           onChange={(e) => setKickoff(e.target.value)} />
                </label>
            </div>
            <div className="admin-form-row">
                {clubs ? (
                    <React.Fragment>
                        <TeamSelect label="Gospodarz" teams={teams} value={team1} onChange={setTeam1} />
                        <TeamSelect label="Gość" teams={teams} value={team2} onChange={setTeam2} />
                    </React.Fragment>
                ) : (
                    <React.Fragment>
                        <CountryField label="Gospodarz" listId="match-team1-countries"
                                      countries={countries} value={team1} onChange={setTeam1} />
                        <CountryField label="Gość" listId="match-team2-countries"
                                      countries={countries} value={team2} onChange={setTeam2} />
                    </React.Fragment>
                )}
                <button className="admin-btn admin-btn-primary" disabled={busy}>
                    {busy ? "Zapisywanie…" : "Dodaj mecz"}
                </button>
            </div>
            {stage === "knockout" && (
                <p className="admin-hint">
                    Drużyny możesz zostawić puste — uzupełnisz je, gdy drabinka się rozstrzygnie.
                </p>
            )}
        </form>
    );
}

function TeamSelect({ label, teams, value, onChange }) {
    return (
        <label>
            {label}
            <select value={value} onChange={(e) => onChange(e.target.value)}>
                <option value="">— nieznana —</option>
                {teams.map((t) => <option key={t.code} value={t.code}>{t.name}</option>)}
            </select>
        </label>
    );
}

function AdminMatchRow({ match, teams, onChanged, onError, onInfo }) {
    const [s1, setS1] = useState(match.actualScore1 ?? "");
    const [s2, setS2] = useState(match.actualScore2 ?? "");
    const [adv, setAdv] = useState(match.advancingCode ?? "");
    const [editing, setEditing] = useState(false);
    const [busy, setBusy] = useState(false);

    const knockout = !!match.roundName;
    const isDraw = s1 !== "" && s2 !== "" && Number(s1) === Number(s2);
    const needsAdvancing = knockout && isDraw;

    async function saveResult() {
        setBusy(true);
        const res = await api(`${API}/admin/matches/${match.id}/result`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                score1: s1 === "" ? null : Number(s1),
                score2: s2 === "" ? null : Number(s2),
                advancingCode: adv || null,
            }),
        });
        setBusy(false);
        if (!res.ok) return onError(await adminError(res, "Nie udało się zapisać wyniku"));
        onInfo("Wynik zapisany, punkty przeliczone");
        onChanged();
    }

    async function clearResult() {
        setBusy(true);
        const res = await api(`${API}/admin/matches/${match.id}/result`, { method: "DELETE" });
        setBusy(false);
        if (!res.ok) return onError(await adminError(res, "Nie udało się wyczyścić wyniku"));
        setS1(""); setS2(""); setAdv("");
        onInfo("Wynik wyczyszczony, punkty przeliczone");
        onChanged();
    }

    async function removeMatch() {
        const ile = match.predictionCount;
        const ostrzezenie = ile > 0
            ? `Ten mecz ma ${ile} ${ile === 1 ? "typ" : "typów"} od użytkowników. `
              + "Usunięcie skasuje je bezpowrotnie. Kontynuować?"
            : "Usunąć ten mecz?";
        if (!window.confirm(ostrzezenie)) return;
        const res = await api(`${API}/admin/matches/${match.id}`, { method: "DELETE" });
        if (!res.ok) return onError(await adminError(res, "Nie udało się usunąć meczu"));
        onInfo("Mecz usunięty");
        onChanged();
    }

    return (
        <div className="admin-match">
            <div className="admin-match-main">
                <span className="admin-match-tag">{match.roundName || `Grupa ${match.groupName}`}</span>
                <span className="admin-match-time">{toLocalInput(match.kickoffUtc).replace("T", " ")}</span>
                <span className="admin-match-teams">
                    {match.team1Name || "—"} <span className="admin-vs">vs</span> {match.team2Name || "—"}
                </span>
                {match.predictionCount > 0 && (
                    <span className="admin-match-preds" title="Liczba oddanych typów">
                        {match.predictionCount} typ.
                    </span>
                )}
            </div>

            <div className="admin-match-controls">
                <input className="admin-score" type="number" min="0" value={s1}
                       disabled={!match.team1Code || !match.team2Code}
                       onChange={(e) => setS1(e.target.value)} />
                <span className="admin-colon">:</span>
                <input className="admin-score" type="number" min="0" value={s2}
                       disabled={!match.team1Code || !match.team2Code}
                       onChange={(e) => setS2(e.target.value)} />

                <button className="admin-btn admin-btn-primary" disabled={busy} onClick={saveResult}>
                    Zapisz
                </button>
                {match.actualScore1 !== null && (
                    <button className="admin-btn" disabled={busy} onClick={clearResult}>Wyczyść</button>
                )}
                <button className="admin-btn" onClick={() => setEditing((v) => !v)}>
                    {editing ? "Zwiń" : "Edytuj"}
                </button>
                <button className="admin-x" title="Usuń mecz" onClick={removeMatch}>×</button>
            </div>

            {needsAdvancing && (
                <div className="admin-advancing">
                    <span>Remis — kto awansował po karnych?</span>
                    <label>
                        <input type="radio" name={`adv-${match.id}`} checked={adv === match.team1Code}
                               onChange={() => setAdv(match.team1Code)} />
                        {match.team1Name}
                    </label>
                    <label>
                        <input type="radio" name={`adv-${match.id}`} checked={adv === match.team2Code}
                               onChange={() => setAdv(match.team2Code)} />
                        {match.team2Name}
                    </label>
                </div>
            )}

            {editing && (
                <AdminMatchEdit match={match} teams={teams} onChanged={onChanged}
                                onError={onError} onInfo={onInfo} onClose={() => setEditing(false)} />
            )}
        </div>
    );
}

function AdminMatchEdit({ match, teams, onChanged, onError, onInfo, onClose }) {
    const [team1, setTeam1] = useState(match.team1Code || "");
    const [team2, setTeam2] = useState(match.team2Code || "");
    const [kickoff, setKickoff] = useState(toLocalInput(match.kickoffUtc));
    const [busy, setBusy] = useState(false);

    async function save() {
        setBusy(true);
        const res = await api(`${API}/admin/matches/${match.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                groupName: match.groupName,
                roundName: match.roundName,
                kickoffUtc: toIsoUtc(kickoff),
                team1Code: team1 || null,
                team2Code: team2 || null,
            }),
        });
        setBusy(false);
        if (!res.ok) return onError(await adminError(res, "Nie udało się zapisać meczu"));
        onInfo("Mecz zaktualizowany");
        onClose();
        onChanged();
    }

    return (
        <div className="admin-match-edit">
            <TeamSelect label="Gospodarz" teams={teams} value={team1} onChange={setTeam1} />
            <TeamSelect label="Gość" teams={teams} value={team2} onChange={setTeam2} />
            <label>
                Początek meczu
                <input type="datetime-local" value={kickoff} onChange={(e) => setKickoff(e.target.value)} />
            </label>
            <button className="admin-btn admin-btn-primary" disabled={busy} onClick={save}>Zapisz mecz</button>
        </div>
    );
}

// ---- Uzytkownicy ----
// Reset hasla: serwer ustawia losowe haslo tymczasowe i zwraca je jednorazowo. Admin przekazuje
// je uzytkownikowi (np. na komunikatorze), a ten zmienia je sam w profilu.

function AdminUsers({ onError, onInfo }) {
    const [users, setUsers] = useState(null);
    const [filter, setFilter] = useState("");
    const [reset, setReset] = useState(null); // { username, temporaryPassword }
    const [busyId, setBusyId] = useState(null);

    useEffect(() => {
        api(`${API}/admin/users`)
            .then(async (res) => {
                if (!res.ok) return onError(await adminError(res, "Nie udało się wczytać użytkowników"));
                setUsers(await res.json());
            });
    }, []);

    async function resetPassword(user) {
        if (!window.confirm(`Zresetować hasło użytkownika ${user.username}? Dotychczasowe hasło przestanie działać.`)) return;
        setBusyId(user.id);
        const res = await api(`${API}/admin/users/${user.id}/reset-password`, { method: "POST" });
        setBusyId(null);
        if (!res.ok) return onError(await adminError(res, "Nie udało się zresetować hasła"));
        setReset(await res.json());
    }

    async function copyPassword() {
        try {
            await navigator.clipboard.writeText(reset.temporaryPassword);
            onInfo("Skopiowano hasło");
        } catch (_) {
            onError("Nie udało się skopiować — przepisz hasło ręcznie");
        }
    }

    if (users === null) return <p className="admin-empty">Wczytywanie…</p>;

    const q = filter.trim().toLowerCase();
    const visible = q ? users.filter((u) => u.username.toLowerCase().includes(q)) : users;

    return (
        <div className="admin-section">
            {reset && (
                <div className="admin-reset">
                    <div>
                        Nowe hasło dla <strong>{reset.username}</strong>:
                        <code className="admin-reset-pass">{reset.temporaryPassword}</code>
                    </div>
                    <p className="admin-hint">
                        Widać je tylko teraz — po zamknięciu nie da się go odczytać. Przekaż je użytkownikowi,
                        a on zmieni je w profilu.
                    </p>
                    <div className="admin-match-controls">
                        <button className="admin-btn admin-btn-primary" onClick={copyPassword}>Kopiuj</button>
                        <button className="admin-btn" onClick={() => setReset(null)}>Zamknij</button>
                    </div>
                </div>
            )}

            <input value={filter} placeholder="Szukaj użytkownika…"
                   onChange={(e) => setFilter(e.target.value)} />

            {visible.length === 0 ? (
                <p className="admin-empty">Brak pasujących użytkowników.</p>
            ) : (
                visible.map((u) => (
                    <div className="admin-match admin-user" key={u.id}>
                        <span className="admin-match-teams">{u.username}</span>
                        {u.admin && <span className="admin-match-tag">admin</span>}
                        <button className="admin-btn admin-user-reset" disabled={busyId === u.id}
                                onClick={() => resetPassword(u)}>
                            Zresetuj hasło
                        </button>
                    </div>
                ))
            )}
        </div>
    );
}

// ---- Panel ----

// Panel admina jest dedykowany rozgrywkom z adresu (?t=<slug>, patrz TOURNAMENT w app.js) -
// zero przelacznika miedzy turniejami, zeby admin nie zmienil czegos w zlych rozgrywkach.
function AdminPanel() {
    const [tournament, setTournament] = useState(null); // undefined = nie znaleziono
    const [teams, setTeams] = useState([]);
    const [matches, setMatches] = useState([]);
    const [view, setView] = useState("matches"); // "matches" | "teams" | "users"
    const [error, setError] = useState("");
    const [info, setInfo] = useState("");

    async function loadTournament() {
        const res = await api(`${API}/admin/tournaments`);
        if (!res.ok) return setError(await adminError(res, "Nie udało się wczytać rozgrywek"));
        const list = await res.json();
        setTournament(list.find((t) => t.slug === TOURNAMENT) || undefined);
    }

    async function loadDetails(id) {
        if (!id) return;
        const [tr, mr] = await Promise.all([
            api(`${API}/admin/tournaments/${id}/teams`),
            api(`${API}/admin/tournaments/${id}/matches`),
        ]);
        if (tr.ok) setTeams(await tr.json());
        if (mr.ok) setMatches(await mr.json());
    }

    useEffect(() => { loadTournament(); }, []);
    useEffect(() => { loadDetails(tournament && tournament.id); }, [tournament]);

    // Komunikat potwierdzenia znika sam, zeby nie zostawal na ekranie po kolejnej akcji
    useEffect(() => {
        if (!info) return;
        const id = setTimeout(() => setInfo(""), 4000);
        return () => clearTimeout(id);
    }, [info]);

    function refresh() {
        setError("");
        loadDetails(tournament && tournament.id);
    }

    function notifyError(message) {
        setInfo("");
        setError(message);
    }

    function notifyInfo(message) {
        setError("");
        setInfo(message);
    }

    if (tournament === null) {
        return <div className="admin-panel"><p className="admin-empty">Wczytywanie…</p></div>;
    }

    if (tournament === undefined) {
        return <div className="admin-panel"><p className="admin-empty">Nie ma rozgrywek o adresie „{TOURNAMENT}”.</p></div>;
    }

    return (
        <div className="admin-panel">
            <AdminNotice error={error} info={info} />

            <div className="admin-subtabs">
                <button className={"chip" + (view === "matches" ? " active" : "")}
                        onClick={() => setView("matches")}>Mecze ({matches.length})</button>
                {tournament.teamKind === "CLUB" && (
                    <button className={"chip" + (view === "teams" ? " active" : "")}
                            onClick={() => setView("teams")}>Drużyny ({teams.length})</button>
                )}
                <button className={"chip" + (view === "users" ? " active" : "")}
                        onClick={() => setView("users")}>Użytkownicy</button>
            </div>

            {view === "users" ? (
                <AdminUsers onError={notifyError} onInfo={notifyInfo} />
            ) : view === "teams" && tournament.teamKind === "CLUB" ? (
                <AdminTeams tournament={tournament} teams={teams} onChanged={refresh}
                            onError={notifyError} onInfo={notifyInfo} />
            ) : (
                <div className="admin-section">
                    <MatchForm tournament={tournament} teams={teams}
                               onCreated={() => { notifyInfo("Mecz dodany"); refresh(); }}
                               onError={notifyError} />
                    {matches.length === 0 ? (
                        <p className="admin-empty">Brak meczów w tych rozgrywkach.</p>
                    ) : (
                        matches.map((m) => (
                            <AdminMatchRow key={m.id} match={m} teams={teams} onChanged={refresh}
                                           onError={notifyError} onInfo={notifyInfo} />
                        ))
                    )}
                </div>
            )}
        </div>
    );
}

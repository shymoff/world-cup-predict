const { useState, useEffect, useMemo } = React;

const API = "/api";

// Fetch z dolaczonym tokenem JWT. Na 401 czysci sesje i wraca do logowania.
function api(path, options = {}) {
    const token = localStorage.getItem("wc_token");
    const headers = { ...(options.headers || {}) };
    if (token) headers["Authorization"] = "Bearer " + token;
    return fetch(path, { ...options, headers }).then((res) => {
        if (res.status === 401) {
            localStorage.removeItem("wc_token");
            localStorage.removeItem("wc_user");
            window.dispatchEvent(new Event("wc-logout"));
        }
        return res;
    });
}

// URL flagi z flagcdn.com na podstawie kodu ISO (np. "pl", "gb-sct")
function flagUrl(code) {
    return `https://flagcdn.com/w40/${code}.png`;
}

const DNI = ["niedziela", "poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota"];
const MIESIACE = ["stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
    "lipca", "sierpnia", "września", "października", "listopada", "grudnia"];

// "2026-06-11" -> { weekday: "czwartek", label: "11 czerwca 2026" }
function formatDate(iso) {
    const [y, mo, d] = iso.split("-").map(Number);
    const dt = new Date(y, mo - 1, d);
    return {
        weekday: DNI[dt.getDay()],
        label: `${d} ${MIESIACE[mo - 1]} ${y}`,
    };
}

// Godziny startu meczu: czas polski (Europe/Warsaw) + pomocniczo czas wschodni USA (ET).
// Przegladarka sama uwzglednia strefy i zmiane czasu na podstawie momentu UTC.
function kickoffInfo(iso, slateDate) {
    const dt = new Date(iso);
    const pl = new Intl.DateTimeFormat("pl-PL",
        { hour: "2-digit", minute: "2-digit", timeZone: "Europe/Warsaw" }).format(dt);
    const et = new Intl.DateTimeFormat("en-GB",
        { hour: "2-digit", minute: "2-digit", timeZone: "America/New_York" }).format(dt);
    // Jesli w Polsce to juz kolejny dzien wzgledem dnia meczowego (mecz nad ranem) - zaznacz date.
    const plIso = new Intl.DateTimeFormat("en-CA",
        { year: "numeric", month: "2-digit", day: "2-digit", timeZone: "Europe/Warsaw" }).format(dt);
    let nextDay = null;
    if (plIso !== slateDate) {
        const [, mm, dd] = plIso.split("-");
        nextDay = `${dd}.${mm}`;
    }
    return { pl, et, nextDay };
}

// Polska odmiana slowa "mecz": 1 mecz, 2-4 mecze, 5+ meczow
function meczeWord(n) {
    if (n === 1) return "mecz";
    const ten = n % 10, hundred = n % 100;
    if (ten >= 2 && ten <= 4 && (hundred < 12 || hundred > 14)) return "mecze";
    return "meczów";
}

function Flag({ code, name }) {
    return <img className="flag" src={flagUrl(code)} alt={name} title={name} loading="lazy" />;
}

// ---- Pojedynczy mecz z edycja wyniku ----
function MatchRow({ match, onSaved }) {
    const [s1, setS1] = useState(match.score1 ?? "");
    const [s2, setS2] = useState(match.score2 ?? "");
    const [saving, setSaving] = useState(false);
    const [justSaved, setJustSaved] = useState(false);

    useEffect(() => {
        setS1(match.score1 ?? "");
        setS2(match.score2 ?? "");
    }, [match.score1, match.score2]);

    // Po rozpoczeciu meczu typowanie jest zablokowane
    const locked = new Date() >= new Date(match.kickoffUtc);

    const dirty =
        String(s1) !== String(match.score1 ?? "") ||
        String(s2) !== String(match.score2 ?? "");
    const bothFilled = s1 !== "" && s2 !== "";

    async function save() {
        if (!bothFilled || locked) return;
        setSaving(true);
        await api(`${API}/matches/${match.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ score1: Number(s1), score2: Number(s2) }),
        });
        setSaving(false);
        setJustSaved(true);
        setTimeout(() => setJustSaved(false), 1500);
        onSaved();
    }

    async function clear() {
        setSaving(true);
        await api(`${API}/matches/${match.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ score1: null, score2: null }),
        });
        setS1("");
        setS2("");
        setSaving(false);
        onSaved();
    }

    function onKey(e) {
        if (e.key === "Enter") save();
    }

    const t = kickoffInfo(match.kickoffUtc, match.date);
    const hasActual = match.actualScore1 != null && match.actualScore2 != null;

    return (
        <div className={"match-row" + (match.played ? " played" : "") + (locked ? " locked" : "")}>
            <div className="match-group">
                <span className="kick">🕑 {t.pl}{t.nextDay ? ` (${t.nextDay})` : ""}</span>
                <span className="dot">·</span>
                <span className="grp">Grupa {match.groupName}</span>
                <span className="et">{t.et} ET</span>
                {hasActual && (
                    <React.Fragment>
                        <span className="dot">·</span>
                        <span className="actual-result">Wynik: {match.actualScore1}:{match.actualScore2}</span>
                    </React.Fragment>
                )}
                {match.pointsEarned != null && (
                    <span className={"points-badge" + (match.pointsEarned > 0 ? " hit" : "")}>
                        {match.pointsEarned > 0 ? `+${match.pointsEarned} pkt` : "0 pkt"}
                    </span>
                )}
            </div>

            <div className="team home">
                <span className="name">{match.team1Name}</span>
                <Flag code={match.team1Code} name={match.team1Name} />
            </div>

            <div className="score-box">
                <input type="number" min="0" value={s1} onKeyDown={onKey} disabled={locked}
                       onChange={(e) => setS1(e.target.value)} />
                <span className="sep">:</span>
                <input type="number" min="0" value={s2} onKeyDown={onKey} disabled={locked}
                       onChange={(e) => setS2(e.target.value)} />
            </div>

            <div className="team away">
                <Flag code={match.team2Code} name={match.team2Name} />
                <span className="name">{match.team2Name}</span>
            </div>

            <div className="row-actions">
                {locked ? (
                    <span className="locked-badge">🔒 Zakłady zamknięte</span>
                ) : (
                    <React.Fragment>
                        <button className="btn btn-save" disabled={!bothFilled || !dirty || saving}
                                onClick={save}>
                            {saving ? "Zapisywanie…" : "Zapisz"}
                        </button>
                        {match.played && (
                            <button className="btn btn-clear" disabled={saving} onClick={clear}>
                                Wyczyść
                            </button>
                        )}
                        {justSaved && <span className="saved-badge">✓ zapisano</span>}
                    </React.Fragment>
                )}
            </div>
        </div>
    );
}

// ---- Sekcja jednego dnia z meczami ----
function DayCard({ date, matches, onSaved }) {
    const { weekday, label } = formatDate(date);
    return (
        <div className="day-card">
            <h2>
                <span className="weekday">{weekday}</span>
                <span className="day-label">{label}</span>
                <span className="day-count">{matches.length} {meczeWord(matches.length)}</span>
            </h2>
            {matches.map((m) => (
                <MatchRow key={m.id} match={m} onSaved={onSaved} />
            ))}
        </div>
    );
}

// ---- Ranking wszystkich zarejestrowanych uzytkownikow ----
const MEDALE = ["🥇", "🥈", "🥉"];

function Leaderboard({ me }) {
    const [entries, setEntries] = useState(null);

    useEffect(() => {
        api(`${API}/leaderboard`).then((res) => {
            if (!res.ok) return;
            res.json().then(setEntries);
        });
    }, []);

    if (entries === null) {
        return <div className="loading">Ładowanie rankingu…</div>;
    }

    return (
        <React.Fragment>
            <div className="leaderboard">
            <table className="leaderboard-table">
                <thead>
                    <tr>
                        <th className="lb-rank">#</th>
                        <th>Użytkownik</th>
                        <th className="lb-points">Punkty</th>
                    </tr>
                </thead>
                <tbody>
                    {entries.map((e, i) => (
                        <tr key={e.username} className={e.username === me ? "me" : ""}>
                            <td className="lb-rank">{i < 3 ? MEDALE[i] : i + 1}</td>
                            <td className="lb-name">
                                {e.username}
                                {e.username === me && <span className="you-badge">Ty</span>}
                            </td>
                            <td className="lb-points">{e.points}</td>
                        </tr>
                    ))}
                </tbody>
            </table>
            </div>
            <div className="prize-card">
                <h2>🎁 Nagroda</h2>
                <p className="prize-text">
                    Za zajęcie <strong>pierwszego miejsca</strong> do wygrania są
                    <strong> 2 złote</strong> oraz <strong>2 piwa</strong>! 🍺
                </p>
                <img src="piwka.png" alt="Piwa - nagroda" className="prize-img" />
                <p className="prize-sponsor">
                    Sponsorem nagród jest <strong>SKLEP SKOLIM</strong>.
                </p>
            </div>
        </React.Fragment>
    );
}

// ---- Typ na mistrza turnieju (15 pkt za trafienie) ----
function ChampionPicker() {
    const [data, setData] = useState(null);
    const [pick, setPick] = useState("");
    const [saving, setSaving] = useState(false);
    const [justSaved, setJustSaved] = useState(false);

    function load() {
        api(`${API}/champion`).then((res) => {
            if (!res.ok) return;
            res.json().then((d) => {
                setData(d);
                setPick(d.pick ?? "");
            });
        });
    }

    useEffect(() => { load(); }, []);

    async function save(code) {
        setSaving(true);
        const res = await api(`${API}/champion`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ code: code || null }),
        });
        setSaving(false);
        if (res.ok) {
            setJustSaved(true);
            setTimeout(() => setJustSaved(false), 1500);
            res.json().then(setData);
        }
    }

    function onPick(e) {
        const code = e.target.value;
        setPick(code);
        save(code);
    }

    function clear() {
        setPick("");
        save("");
    }

    if (data === null) {
        return <div className="loading">Ładowanie…</div>;
    }

    const { teams, locked, actualChampion, pointsEarned } = data;
    const pickedTeam = teams.find((t) => t.code === pick);
    const championTeam = teams.find((t) => t.code === actualChampion);

    return (
        <div className="champion-card">
            <h2>🏆 Typ na mistrza turnieju</h2>
            <p className="champion-info">
                Wybierz drużynę, która Twoim zdaniem wygra Mistrzostwa Świata 2026.
                Za trafiony typ otrzymasz <strong>15 punktów</strong> tuż po finale.
            </p>

            {actualChampion && (
                <div className="champion-result">
                    <span>Mistrz turnieju:</span>
                    {championTeam && <Flag code={championTeam.code} name={championTeam.name} />}
                    <strong>{championTeam ? championTeam.name : actualChampion}</strong>
                    <span className={"points-badge" + (pointsEarned > 0 ? " hit" : "")}>
                        {pointsEarned > 0 ? `+${pointsEarned} pkt` : "0 pkt"}
                    </span>
                </div>
            )}

            <div className="champion-picker">
                {pickedTeam && <Flag code={pickedTeam.code} name={pickedTeam.name} />}
                <select value={pick} disabled={locked || saving} onChange={onPick}>
                    <option value="">— wybierz drużynę —</option>
                    {teams.map((t) => (
                        <option key={t.code} value={t.code}>{t.name}</option>
                    ))}
                </select>

                {locked ? (
                    <span className="locked-badge">🔒 Typowanie zamknięte</span>
                ) : (
                    <React.Fragment>
                        {pick && (
                            <button className="btn btn-clear" disabled={saving} onClick={clear}>
                                Wyczyść
                            </button>
                        )}
                        {justSaved && <span className="saved-badge">✓ zapisano</span>}
                    </React.Fragment>
                )}
            </div>
        </div>
    );
}

// ---- Pojedynczy mecz fazy pucharowej (wynik + przy remisie wskazanie awansu) ----
function KnockoutMatchRow({ match, onSaved }) {
    const [s1, setS1] = useState(match.score1 ?? "");
    const [s2, setS2] = useState(match.score2 ?? "");
    const [adv, setAdv] = useState(match.advancing ?? "");
    const [saving, setSaving] = useState(false);
    const [justSaved, setJustSaved] = useState(false);

    useEffect(() => {
        setS1(match.score1 ?? "");
        setS2(match.score2 ?? "");
        setAdv(match.advancing ?? "");
    }, [match.score1, match.score2, match.advancing]);

    // Drużyny pucharowe są znane dopiero po fazie grupowej - do tego czasu nie da się typować.
    const teamsKnown = !!(match.team1Code && match.team2Code);
    const locked = !teamsKnown || new Date() >= new Date(match.kickoffUtc);
    const bothFilled = s1 !== "" && s2 !== "";
    const isDraw = bothFilled && Number(s1) === Number(s2);
    // Przy remisie trzeba wskazac druzyne awansujaca (karne); przy wygranej wynika z wyniku.
    const advForDraw = isDraw ? adv : "";
    const complete = bothFilled && (!isDraw || adv !== "");

    const dirty =
        String(s1) !== String(match.score1 ?? "") ||
        String(s2) !== String(match.score2 ?? "") ||
        String(advForDraw) !== String(isDrawSaved(match) ? (match.advancing ?? "") : "");

    async function save() {
        if (!complete || locked) return;
        setSaving(true);
        await api(`${API}/matches/${match.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                score1: Number(s1),
                score2: Number(s2),
                advancingCode: isDraw ? adv : null,
            }),
        });
        setSaving(false);
        setJustSaved(true);
        setTimeout(() => setJustSaved(false), 1500);
        onSaved();
    }

    async function clear() {
        setSaving(true);
        await api(`${API}/matches/${match.id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ score1: null, score2: null, advancingCode: null }),
        });
        setS1("");
        setS2("");
        setAdv("");
        setSaving(false);
        onSaved();
    }

    function onKey(e) {
        if (e.key === "Enter") save();
    }

    const t = kickoffInfo(match.kickoffUtc, match.date);
    const kd = formatDate(match.date);
    const hasActual = match.actualScore1 != null && match.actualScore2 != null;
    const actualAdvName = match.actualAdvancing === match.team1Code ? match.team1Name
        : match.actualAdvancing === match.team2Code ? match.team2Name : null;

    return (
        <div className={"match-row knockout-row" + (match.played ? " played" : "") + (locked ? " locked" : "")}>
            <div className="match-group">
                <span className="kdate">📅 {kd.weekday}, {kd.label}</span>
                <span className="dot">·</span>
                <span className="kick">🕑 {t.pl}</span>
                <span className="dot">·</span>
                <span className="grp">Faza pucharowa</span>
                <span className="et">{t.et} ET</span>
                {hasActual && (
                    <React.Fragment>
                        <span className="dot">·</span>
                        <span className="actual-result">
                            Wynik: {match.actualScore1}:{match.actualScore2}
                            {actualAdvName ? ` (awans: ${actualAdvName})` : ""}
                        </span>
                    </React.Fragment>
                )}
                {match.pointsEarned != null && (
                    <span className={"points-badge" + (match.pointsEarned > 0 ? " hit" : "")}>
                        {match.pointsEarned > 0 ? `+${match.pointsEarned} pkt` : "0 pkt"}
                    </span>
                )}
            </div>

            <div className="team home">
                <span className={"name" + (match.team1Code ? "" : " tbd")}>{match.team1Name || "do ustalenia"}</span>
                {match.team1Code && <Flag code={match.team1Code} name={match.team1Name} />}
            </div>

            <div className="score-box">
                <input type="number" min="0" value={s1} onKeyDown={onKey} disabled={locked}
                       onChange={(e) => setS1(e.target.value)} />
                <span className="sep">:</span>
                <input type="number" min="0" value={s2} onKeyDown={onKey} disabled={locked}
                       onChange={(e) => setS2(e.target.value)} />
            </div>

            <div className="team away">
                {match.team2Code && <Flag code={match.team2Code} name={match.team2Name} />}
                <span className={"name" + (match.team2Code ? "" : " tbd")}>{match.team2Name || "do ustalenia"}</span>
            </div>

            {isDraw && (
                <div className="advancing-pick">
                    <span className="advancing-label">Kto awansuje po karnych?</span>
                    <div className="advancing-options">
                        <button type="button" disabled={locked}
                                className={"adv-btn" + (adv === match.team1Code ? " active" : "")}
                                onClick={() => setAdv(match.team1Code)}>
                            <Flag code={match.team1Code} name={match.team1Name} />
                            {match.team1Name}
                        </button>
                        <button type="button" disabled={locked}
                                className={"adv-btn" + (adv === match.team2Code ? " active" : "")}
                                onClick={() => setAdv(match.team2Code)}>
                            <Flag code={match.team2Code} name={match.team2Name} />
                            {match.team2Name}
                        </button>
                    </div>
                </div>
            )}

            <div className="row-actions">
                {!teamsKnown ? (
                    <span className="locked-badge">⏳ Drużyny po fazie grupowej</span>
                ) : locked ? (
                    <span className="locked-badge">🔒 Zakłady zamknięte</span>
                ) : (
                    <React.Fragment>
                        <button className="btn btn-save" disabled={!complete || !dirty || saving}
                                onClick={save}>
                            {saving ? "Zapisywanie…" : "Zapisz"}
                        </button>
                        {match.played && (
                            <button className="btn btn-clear" disabled={saving} onClick={clear}>
                                Wyczyść
                            </button>
                        )}
                        {justSaved && <span className="saved-badge">✓ zapisano</span>}
                        {isDraw && adv === "" && (
                            <span className="advancing-hint">Wskaż drużynę awansującą</span>
                        )}
                    </React.Fragment>
                )}
            </div>
        </div>
    );
}

// Czy zapisany typ to remis (wtedy ma znaczenie zapamietany kod awansu)
function isDrawSaved(match) {
    return match.score1 != null && match.score2 != null && match.score1 === match.score2;
}

// ---- Faza pucharowa: typ na mistrza + mecze pogrupowane wg rund ----
const RUNDY_PUCHAROWE = ["1/16", "1/8", "Ćwierćfinał", "Półfinał", "Mecz o 3. miejsce", "Finał"];

function KnockoutStage({ matches, onSaved }) {
    const byRound = useMemo(() => {
        const map = new Map();
        matches.forEach((m) => {
            if (!map.has(m.roundName)) map.set(m.roundName, []);
            map.get(m.roundName).push(m);
        });
        return map;
    }, [matches]);

    return (
        <div className="knockout">
            <ChampionPicker />
            <div className="knockout-rules">
                <h2>📋 Punktacja fazy pucharowej</h2>
                <ul>
                    <li><strong>4 pkt</strong> — dokładny wynik (czyli też trafiony awans)</li>
                    <li><strong>3 pkt</strong> — dokładny remis, ale zły typ drużyny awansującej</li>
                    <li><strong>2 pkt</strong> — niedokładny wynik, ale trafiony awans</li>
                    <li><strong>1 pkt</strong> — niedokładny remis i zły typ drużyny awansującej</li>
                </ul>
            </div>
            {RUNDY_PUCHAROWE.map((runda) => {
                const list = byRound.get(runda) || [];
                return (
                    <div className="knockout-section" key={runda}>
                        <h2>{runda}</h2>
                        {list.length === 0 ? (
                            <p className="knockout-placeholder">Mecze zostaną uzupełnione po zakończeniu fazy grupowej.</p>
                        ) : (
                            list.map((m) => (
                                <KnockoutMatchRow key={m.id} match={m} onSaved={onSaved} />
                            ))
                        )}
                    </div>
                );
            })}
        </div>
    );
}

// ---- Aplikacja (dla zalogowanego uzytkownika) ----
function App({ user, onLogout }) {
    const [matches, setMatches] = useState([]);
    const [groupFilter, setGroupFilter] = useState("ALL");
    const [loading, setLoading] = useState(true);
    const [tab, setTab] = useState("matches"); // "matches" | "knockout" | "leaderboard"

    async function loadAll() {
        const res = await api(`${API}/matches`);
        if (!res.ok) return; // np. 401 - obsluzone globalnie (powrot do logowania)
        setMatches(await res.json());
        setLoading(false);
    }

    useEffect(() => { loadAll(); }, []);

    // Co 30 s odswiezamy widok, aby mecze rozpoczynajace sie "na zywo" same sie zablokowaly
    const [, setTick] = useState(0);
    useEffect(() => {
        const id = setInterval(() => setTick((x) => x + 1), 30000);
        return () => clearInterval(id);
    }, []);

    // Mecze fazy grupowej (zakladka "Mecze") oraz pucharowej (zakladka "Faza pucharowa")
    const groupMatches = useMemo(() => matches.filter((m) => !m.roundName), [matches]);
    const koMatches = useMemo(() => matches.filter((m) => m.roundName), [matches]);

    const groups = useMemo(
        () => [...new Set(groupMatches.map((m) => m.groupName))].sort(),
        [groupMatches]
    );

    const filtered = useMemo(
        () => (groupFilter === "ALL"
            ? groupMatches
            : groupMatches.filter((m) => m.groupName === groupFilter)),
        [groupMatches, groupFilter]
    );

    // Grupowanie po dacie z zachowaniem kolejnosci chronologicznej
    const byDate = useMemo(() => {
        const map = new Map();
        filtered.forEach((m) => {
            if (!map.has(m.date)) map.set(m.date, []);
            map.get(m.date).push(m);
        });
        return [...map.entries()]; // [ [date, [matches]], ... ]
    }, [filtered]);

    const playedCount = matches.filter((m) => m.played).length;
    const playedPct = matches.length ? Math.round((playedCount / matches.length) * 100) : 0;

    if (loading) {
        return <div className="loading">Ładowanie meczów…</div>;
    }

    return (
        <div>
            <header className="app-header">
                <div className="userbar">
                    <span className="user-chip">
                        <span className="user-avatar">{user.charAt(0).toUpperCase()}</span>
                        {user}
                    </span>
                    <button className="btn btn-clear" onClick={onLogout}>Wyloguj</button>
                </div>
                <h1>⚽ Mistrzostwa Świata <span className="grad">2026</span></h1>
                <p className="tagline">Typuj wyniki • Faza grupowa • 11–27 czerwca 2026</p>
                <div className="progress">
                    <div className="progress-track">
                        <div className="progress-fill" style={{ width: `${playedPct}%` }} />
                    </div>
                    <span className="progress-label">{playedCount} / {matches.length} typów</span>
                </div>
            </header>

            <div className="container">
                <div className="tab-bar-wrap">
                    <div className="tab-bar">
                        <button className={"chip wide" + (tab === "matches" ? " active" : "")}
                                onClick={() => setTab("matches")}>⚽ Mecze</button>
                        <button className={"chip wide" + (tab === "knockout" ? " active" : "")}
                                onClick={() => setTab("knockout")}>🏁 Faza pucharowa</button>
                        <button className={"chip wide" + (tab === "leaderboard" ? " active" : "")}
                                onClick={() => setTab("leaderboard")}>🏆 Ranking</button>
                    </div>
                </div>

                {tab === "leaderboard" ? (
                    <Leaderboard me={user} />
                ) : tab === "knockout" ? (
                    <KnockoutStage matches={koMatches} onSaved={loadAll} />
                ) : (
                    <React.Fragment>
                        <div className="group-filter">
                            <button className={"chip wide" + (groupFilter === "ALL" ? " active" : "")}
                                    onClick={() => setGroupFilter("ALL")}>Wszystkie</button>
                            {groups.map((g) => (
                                <button key={g}
                                        className={"chip" + (groupFilter === g ? " active" : "")}
                                        onClick={() => setGroupFilter(g)}>{g}</button>
                            ))}
                        </div>

                        {byDate.map(([date, dayMatches]) => (
                            <DayCard key={date} date={date} matches={dayMatches} onSaved={loadAll} />
                        ))}
                    </React.Fragment>
                )}
            </div>

            <footer>
                Terminarz wg oficjalnego losowania (5.12.2025) •
                Flagi: <a href="https://flagcdn.com" target="_blank">flagcdn.com</a> •
                Spring Boot + React
            </footer>
        </div>
    );
}

// ---- Ekran logowania / rejestracji ----
function Login({ onLogin }) {
    const [mode, setMode] = useState("login"); // "login" | "register"
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [password2, setPassword2] = useState("");
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);

    const isRegister = mode === "register";

    function switchMode() {
        setMode(isRegister ? "login" : "register");
        setError("");
        setPassword2("");
    }

    async function submit(e) {
        e.preventDefault();
        setError("");

        if (isRegister && password !== password2) {
            setError("Hasła nie są takie same");
            return;
        }

        setBusy(true);
        const res = await fetch(`${API}/auth/${isRegister ? "register" : "login"}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password }),
        });
        setBusy(false);

        if (!res.ok) {
            let msg = isRegister ? "Nie udało się zarejestrować" : "Nieprawidłowy login lub hasło";
            try {
                const body = await res.json();
                if (body.error) msg = body.error;
            } catch (_) { /* ignore */ }
            setError(msg);
            return;
        }

        const data = await res.json();
        localStorage.setItem("wc_token", data.token);
        localStorage.setItem("wc_user", data.username);
        onLogin(data.username);
    }

    return (
        <div className="login-wrap">
            <form className="login-card" onSubmit={submit}>
                <div className="login-logo">⚽</div>
                <h1>MŚ 2026 — Predyktor</h1>
                <p className="login-sub">
                    {isRegister ? "Załóż konto, aby dołączyć" : "Zaloguj się, aby obstawiać mecze"}
                </p>

                <label>Nazwa użytkownika</label>
                <input type="text" value={username} autoFocus
                       placeholder="np. Szymon"
                       onChange={(e) => setUsername(e.target.value)} />

                <label>Hasło</label>
                <input type="password" value={password}
                       onChange={(e) => setPassword(e.target.value)} />

                {isRegister && (
                    <React.Fragment>
                        <label>Powtórz hasło</label>
                        <input type="password" value={password2}
                               onChange={(e) => setPassword2(e.target.value)} />
                    </React.Fragment>
                )}

                {error && <div className="login-error">{error}</div>}

                <button className="btn btn-save login-btn" type="submit" disabled={busy}>
                    {busy
                        ? (isRegister ? "Rejestrowanie…" : "Logowanie…")
                        : (isRegister ? "Zarejestruj" : "Zaloguj")}
                </button>

                <div className="login-switch">
                    {isRegister ? "Masz już konto?" : "Nie masz konta?"}{" "}
                    <button type="button" className="link-btn" onClick={switchMode}>
                        {isRegister ? "Zaloguj się" : "Zarejestruj się"}
                    </button>
                </div>
            </form>
        </div>
    );
}

// ---- Ekran "serwer sie budzi" (darmowy plan Render usypia po 15 min nieaktywnosci) ----
function WakeScreen() {
    return (
        <div className="login-wrap">
            <div className="login-card wake-card">
                <div className="wake-spinner" />
                <h1>⚽ MŚ 2026 — Predyktor</h1>
                <p className="login-sub">Serwer się uruchamia po dłuższej nieaktywności…</p>
                <p className="wake-sub">To może potrwać do minuty. Strona załaduje się sama.</p>
            </div>
        </div>
    );
}

// Czeka, az backend odpowie na /api/health. Jesli zajmie to dluzej niz 2 s,
// uznajemy, ze instancja sie "budzi" i pokazujemy WakeScreen do skutku.
function useServerWake() {
    const [state, setState] = useState("checking"); // "checking" | "waking" | "ready"

    useEffect(() => {
        let cancelled = false;
        const slowTimer = setTimeout(() => {
            if (!cancelled) setState("waking");
        }, 2000);

        async function waitForServer() {
            while (!cancelled) {
                try {
                    const res = await fetch(`${API}/health`, { cache: "no-store" });
                    if (res.ok) {
                        clearTimeout(slowTimer);
                        if (!cancelled) setState("ready");
                        return;
                    }
                } catch (_) {
                    // serwer jeszcze nie odpowiada - sprobuj ponownie
                }
                await new Promise((r) => setTimeout(r, 3000));
            }
        }

        waitForServer();
        return () => {
            cancelled = true;
            clearTimeout(slowTimer);
        };
    }, []);

    return state;
}

// ---- Korzen: zarzadza sesja (token w localStorage) ----
function Root() {
    const serverState = useServerWake();
    const [user, setUser] = useState(localStorage.getItem("wc_user"));

    useEffect(() => {
        const onLogout = () => setUser(null);
        window.addEventListener("wc-logout", onLogout);
        return () => window.removeEventListener("wc-logout", onLogout);
    }, []);

    function logout() {
        localStorage.removeItem("wc_token");
        localStorage.removeItem("wc_user");
        setUser(null);
    }

    if (serverState === "waking") return <WakeScreen />;
    if (serverState === "checking") return null; // krotka chwila - unikamy mrugniecia ekranu

    if (!user) return <Login onLogin={setUser} />;
    return <App user={user} onLogout={logout} />;
}

ReactDOM.createRoot(document.getElementById("root")).render(<Root />);

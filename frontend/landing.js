const { useState, useEffect } = React;

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

/* Lista gier/rozgrywek na hubie - kolejne kafelki dodawaj tutaj */
const GAMES = [
    {
        id: "worldcup",
        title: "Mistrzostwa Świata 2026",
        description: "Typuj wyniki meczów mundialu, wybierz mistrza i walcz o puchar w rankingu ze znajomymi.",
        href: "/worldcup/",
        image: "worldcup/mundial_trophy.png",
        badge: "Dostępne",
        available: true,
    },
    {
        id: "coming-soon",
        title: "Kolejne rozgrywki",
        description: "Nowe konkursy i typowania pojawią się niebawem. Śledź ParlayHub!",
        badge: "Wkrótce",
        available: false,
    },
];

function Logo({ size = 40 }) {
    return (
        <svg className="logo-mark" style={{ width: size, height: size }} viewBox="0 0 40 40" aria-hidden="true">
            <rect x="4" y="4" width="32" height="32" rx="9" fill="url(#lg)"/>
            <path d="M14 28V12h7a5 5 0 0 1 0 10h-4" fill="none" stroke="#0a0a0b" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round"/>
            <defs>
                <linearGradient id="lg" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%" stopColor="#fb923c"/>
                    <stop offset="100%" stopColor="#ea580c"/>
                </linearGradient>
            </defs>
        </svg>
    );
}

function Wordmark() {
    return <span className="wordmark">Parlay<span className="wordmark-accent">Hub</span></span>;
}

// ---- Polka z trofeami ----
// Kazde trofeum dopasowuje wygrane turnieje po slowie kluczowym w nazwie
const TROPHIES = [
    { id: "mundial", name: "Mistrzostwa Świata", image: "worldcup/mundial_trophy.png", match: /mistrzostwa świata|mundial/i },
    { id: "euro", name: "Euro", image: "worldcup/euro_trophy.png", match: /euro/i },
    { id: "nations", name: "Liga Narodów", image: "worldcup/nations_league_trophy.png", match: /liga narodów|nations/i },
    { id: "club", name: "Klubowe MŚ", image: "worldcup/club_world_club_trophy.png", match: /klubowe|club/i },
];

// Medal SVG za 2. (srebro) lub 3. (braz) miejsce
function Medal({ place }) {
    const disc = place === 2
        ? { light: "#e8eaee", mid: "#b6bcc6", dark: "#8a91a0", rim: "#6b7280" }
        : { light: "#e8a662", mid: "#c67c33", dark: "#9a5a1e", rim: "#7c4a16" };
    const gid = `medal${place}`;
    return (
        <svg className="medal-svg" viewBox="0 0 80 110" aria-hidden="true">
            <defs>
                <linearGradient id={`${gid}-d`} x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0%" stopColor={disc.light}/>
                    <stop offset="55%" stopColor={disc.mid}/>
                    <stop offset="100%" stopColor={disc.dark}/>
                </linearGradient>
            </defs>
            {/* wstazka */}
            <path d="M26 2 L40 34 L54 2 L70 2 L48 48 L32 48 L10 2 Z" fill="#b91c1c"/>
            <path d="M26 2 L40 34 L32 48 L10 2 Z" fill="#dc2626"/>
            {/* krazek */}
            <circle cx="40" cy="72" r="34" fill={disc.rim}/>
            <circle cx="40" cy="72" r="30" fill={`url(#${gid}-d)`}/>
            <circle cx="40" cy="72" r="24" fill="none" stroke={disc.rim} strokeWidth="2" opacity="0.55"/>
            <text x="40" y="84" textAnchor="middle" fontFamily="Sora, sans-serif" fontWeight="800"
                  fontSize="34" fill={disc.rim}>{place}</text>
        </svg>
    );
}

function TrophyShelf({ wonTournaments }) {
    // dla kazdego turnieju: puchar za 1. miejsce oraz medale za 2. i 3.
    // (place moze byc brak w starszych danych = wygrana)
    const groups = TROPHIES.map((t) => {
        const inTournament = wonTournaments.filter((w) => t.match.test(w.name));
        return {
            ...t,
            gold: inTournament.filter((w) => w.place == null || w.place === 1).length,
            silver: inTournament.filter((w) => w.place === 2).length,
            bronze: inTournament.filter((w) => w.place === 3).length,
        };
    });

    return (
        <section className="trophy-shelf-section">
            <h3 className="section-heading">🏆 Gablota</h3>
            <div className="trophy-shelf">
                {groups.map((g) => (
                    <div key={g.id} className="trophy-group" title={g.name}>
                        <div className={"trophy-img-wrap" + (g.gold === 0 ? " shelf-item-empty" : "")}>
                            <img src={g.image} alt={g.name} loading="lazy" />
                            <span className="trophy-count">{g.gold}</span>
                        </div>
                        <div className={"medal-wrap" + (g.silver === 0 ? " shelf-item-empty" : "")}>
                            <Medal place={2} />
                            <span className="trophy-count medal-count">{g.silver}</span>
                        </div>
                        <div className={"medal-wrap" + (g.bronze === 0 ? " shelf-item-empty" : "")}>
                            <Medal place={3} />
                            <span className="trophy-count medal-count">{g.bronze}</span>
                        </div>
                    </div>
                ))}
            </div>
            <div className="shelf-board" />
            <div className="trophy-labels">
                {groups.map((g) => (
                    <span key={g.id} className={"trophy-name" + (g.gold + g.silver + g.bronze === 0 ? " trophy-name-empty" : "")}>{g.name}</span>
                ))}
            </div>
        </section>
    );
}

// ---- Zawartosc zakladki "Statystyki" ----
function ProfileStats({ profile }) {
    if (profile === null) {
        return <div className="loading">Ładowanie profilu…</div>;
    }

    const hitRate = profile.settledPredictions > 0
        ? Math.round((profile.hitPredictions / profile.settledPredictions) * 100)
        : 0;

    return (
        <React.Fragment>
            <p className="profile-hint">
                {profile.wonTournaments.length > 0
                    ? "Podium: " + profile.wonTournaments
                        .map((t) => t.name + (t.place && t.place > 1 ? ` (${t.place}. miejsce)` : ""))
                        .join(", ")
                    : (profile.rank === 1
                        ? "Turniej trwa — obecnie na 1. miejscu!"
                        : "Jeszcze żadnego podium — obecnie " + profile.rank + ". miejsce w rankingu.")}
            </p>

            <div className="profile-stats-grid">
                <div className="stat-box">
                    <span className="stat-value">{profile.points}</span>
                    <span className="stat-label">Punkty</span>
                </div>
                <div className="stat-box">
                    <span className="stat-value">#{profile.rank}</span>
                    <span className="stat-label">Pozycja z {profile.totalUsers}</span>
                </div>
                <div className="stat-box">
                    <span className="stat-value">{profile.predictionsMade}</span>
                    <span className="stat-label">Typów oddanych</span>
                </div>
                <div className="stat-box">
                    <span className="stat-value">{profile.exactHits}</span>
                    <span className="stat-label">Dokładnych wyników</span>
                </div>
                <div className="stat-box">
                    <span className="stat-value">{hitRate}%</span>
                    <span className="stat-label">Skuteczność</span>
                </div>
                <div className="stat-box">
                    <span className="stat-value">
                        {profile.championPickName
                            ? (profile.championPickCorrect === null
                                ? profile.championPickName
                                : (profile.championPickCorrect ? "✅" : "❌") + " " + profile.championPickName)
                            : "—"}
                    </span>
                    <span className="stat-label">Typ na mistrza</span>
                </div>
            </div>
        </React.Fragment>
    );
}

// ---- Zawartosc zakladki "Haslo" ----
function ChangePasswordForm() {
    const [oldPassword, setOldPassword] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [newPassword2, setNewPassword2] = useState("");
    const [error, setError] = useState("");
    const [success, setSuccess] = useState(false);
    const [busy, setBusy] = useState(false);

    async function submit(e) {
        e.preventDefault();
        setError("");
        setSuccess(false);

        if (newPassword !== newPassword2) {
            setError("Nowe hasła nie są takie same");
            return;
        }

        setBusy(true);
        const res = await api(`${API}/user/password`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ oldPassword, newPassword }),
        });
        setBusy(false);

        if (!res.ok) {
            let msg = "Nie udało się zmienić hasła";
            try {
                const body = await res.json();
                if (body.error) msg = body.error;
            } catch (_) { /* ignore */ }
            setError(msg);
            return;
        }

        setSuccess(true);
        setOldPassword("");
        setNewPassword("");
        setNewPassword2("");
    }

    return (
        <form className="password-form" onSubmit={submit}>
            <label>Aktualne hasło</label>
            <input type="password" value={oldPassword}
                   onChange={(e) => setOldPassword(e.target.value)} />

            <label>Nowe hasło</label>
            <input type="password" value={newPassword}
                   onChange={(e) => setNewPassword(e.target.value)} />

            <label>Powtórz nowe hasło</label>
            <input type="password" value={newPassword2}
                   onChange={(e) => setNewPassword2(e.target.value)} />

            {error && <div className="form-error">{error}</div>}
            {success && <div className="form-success">✓ Hasło zostało zmienione</div>}

            <button className="btn-primary" type="submit" disabled={busy}>
                {busy ? "Zapisywanie…" : "Zmień hasło"}
            </button>
        </form>
    );
}

// ---- Naglowek huba (dla zalogowanego) ----
function Header({ user, onLogout }) {
    return (
        <header className="hub-header">
            <a className="brand" href="/">
                <Logo/>
                <Wordmark/>
            </a>
            <div className="userbar">
                <a className="user-chip" href="/account" title="Twoje konto">
                    <span className="user-avatar">{user.charAt(0).toUpperCase()}</span>
                    {user}
                </a>
                <button type="button" className="btn-ghost" onClick={onLogout}>Wyloguj</button>
            </div>
        </header>
    );
}

function Hero() {
    return (
        <section className="hero">
            <h1>Typuj. Rywalizuj. <span className="hero-accent">Wygrywaj.</span></h1>
            <p className="tagline">
                ParlayHub to miejsce, gdzie typujesz wyniki, rywalizujesz ze znajomymi
                i zgarniasz chwałę. Wybierz rozgrywki i zacznij grać.
            </p>
        </section>
    );
}

function GameTile({ game }) {
    if (!game.available) {
        return (
            <div className="tile tile-soon">
                <div className="tile-media tile-media-soon">
                    <span className="soon-mark">?</span>
                </div>
                <div className="tile-body">
                    <span className="badge badge-soon">{game.badge}</span>
                    <h2>{game.title}</h2>
                    <p>{game.description}</p>
                </div>
            </div>
        );
    }
    return (
        <a className="tile tile-active" href={game.href}>
            <div className="tile-media">
                <img src={game.image} alt="" loading="lazy"/>
            </div>
            <div className="tile-body">
                <span className="badge">{game.badge}</span>
                <h2>{game.title}</h2>
                <p>{game.description}</p>
                <span className="cta">Graj teraz <span className="cta-arrow">→</span></span>
            </div>
        </a>
    );
}

function GamesGrid() {
    return (
        <section className="games">
            <h3 className="games-heading">Rozgrywki</h3>
            <div className="games-grid">
                {GAMES.map(g => <GameTile key={g.id} game={g}/>)}
            </div>
        </section>
    );
}

function Footer() {
    return (
        <footer className="hub-footer">
            <span>© 2026 ParlayHub</span>
        </footer>
    );
}

// ---- Hub (dla zalogowanego uzytkownika) ----
function Hub({ user, onLogout }) {
    return (
        <div className="hub">
            <Header user={user} onLogout={onLogout}/>
            <main>
                <Hero/>
                <GamesGrid/>
            </main>
            <Footer/>
        </div>
    );
}

// ---- Strona konta: gablota + statystyki + zmiana hasla ----
// Z parametrem ?user=<nazwa> pokazuje gablote innego uzytkownika (bez statystyk i hasla).
function AccountPage({ user, onLogout }) {
    const viewedParam = new URLSearchParams(window.location.search).get("user");
    const isOwn = !viewedParam || viewedParam.toLowerCase() === user.toLowerCase();
    const shownUser = isOwn ? user : viewedParam;

    const [profile, setProfile] = useState(null);       // wlasny profil
    const [podium, setPodium] = useState(null);          // podium innego uzytkownika
    const [notFound, setNotFound] = useState(false);
    const [tab, setTab] = useState("stats"); // "stats" | "password"

    useEffect(() => {
        if (isOwn) {
            api(`${API}/user/profile`).then((res) => {
                if (!res.ok) return;
                res.json().then(setProfile);
            });
        } else {
            api(`${API}/user/${encodeURIComponent(viewedParam)}/podium`).then((res) => {
                if (res.status === 404) { setNotFound(true); return; }
                if (!res.ok) return;
                res.json().then(setPodium);
            });
        }
    }, [isOwn, viewedParam]);

    return (
        <div className="hub">
            <Header user={user} onLogout={onLogout}/>
            <main className="account-main">
                <a className="back-link" href={isOwn ? "/" : "/worldcup/"}>
                    {isOwn ? "← Wróć na stronę główną" : "← Wróć"}
                </a>

                <div className="profile-head account-head">
                    <span className="user-avatar profile-avatar">{shownUser.charAt(0).toUpperCase()}</span>
                    <h1>{shownUser}</h1>
                </div>

                {notFound ? (
                    <p className="profile-hint">Nie ma takiego użytkownika.</p>
                ) : (
                    <TrophyShelf wonTournaments={isOwn
                        ? (profile ? profile.wonTournaments : [])
                        : (podium || [])}/>
                )}

                {isOwn && (
                    <React.Fragment>
                        <div className="profile-tabs account-tabs">
                            <button className={"chip" + (tab === "stats" ? " active" : "")}
                                    onClick={() => setTab("stats")}>📊 Statystyki</button>
                            <button className={"chip" + (tab === "password" ? " active" : "")}
                                    onClick={() => setTab("password")}>🔒 Hasło</button>
                        </div>

                        <div className="account-panel">
                            {tab === "stats" ? (
                                <ProfileStats profile={profile} />
                            ) : (
                                <ChangePasswordForm />
                            )}
                        </div>
                    </React.Fragment>
                )}
            </main>
            <Footer/>
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
                <div className="login-brand">
                    <Logo size={52}/>
                    <Wordmark/>
                </div>
                <p className="login-sub">
                    {isRegister ? "Załóż konto, aby dołączyć do gry" : "Zaloguj się, aby wejść"}
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

                {error && <div className="form-error">{error}</div>}

                <button className="btn-primary" type="submit" disabled={busy}>
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
                <div className="login-brand">
                    <Logo size={52}/>
                    <Wordmark/>
                </div>
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
    if (window.location.pathname.startsWith("/account")) {
        return <AccountPage user={user} onLogout={logout} />;
    }
    return <Hub user={user} onLogout={logout} />;
}

ReactDOM.createRoot(document.getElementById("root")).render(<Root />);

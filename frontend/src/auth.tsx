import { useCallback, useEffect, useState, type FormEvent } from "react";
import { App } from "./App";
import { configureSession } from "./api";

export interface StaffSession {
  enabled: boolean;
  username: string;
  manager: boolean;
  csrfToken: string;
}

export function StaffWorkspace() {
  const [session, setSession] = useState<StaffSession | null>(null);
  const [username, setUsername] = useState("teller-1");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    const response = await fetch("/api/auth", { cache: "no-store", signal: AbortSignal.timeout(8000) });
    if (!response.ok) throw new Error("The workspace is unavailable. Try again.");
    const value = await response.json() as StaffSession;
    configureSession(value);
    setSession(value);
    return value;
  }, []);
  useEffect(() => {
    void load().catch((e: Error) => setError(e.message));
    const expired = () => { void load().catch(() => setError("Reconnect to sign in again.")); };
    window.addEventListener("banker:signin", expired);
    return () => window.removeEventListener("banker:signin", expired);
  }, [load]);
  async function login(event: FormEvent) {
    event.preventDefault();
    setBusy(true); setError("");
    try {
      const current = await load();
      const response = await fetch("/api/login", {
        method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded", "X-CSRF-TOKEN": current.csrfToken },
        body: new URLSearchParams({ username, password }), signal: AbortSignal.timeout(10000),
      });
      if (!response.ok) throw new Error("The staff username or password is incorrect.");
      setPassword("");
      await load();
    } catch (e) { setError(e instanceof Error ? e.message : "Sign-in failed. Try again."); }
    finally { setBusy(false); }
  }
  async function logout() {
    setError("");
    try {
      const response = await fetch("/api/logout", { method: "POST", headers: { "X-CSRF-TOKEN": session!.csrfToken } });
      if (!response.ok) throw new Error("Sign-out failed. Try again.");
      setSession(null);
      await load();
    } catch (e) { setError(e instanceof Error ? e.message : "Sign-out failed."); }
  }
  if (session && (!session.enabled || session.username)) {
    return <><App key={session.username} staff={session} onSignOut={() => void logout()} />{error && <p role="alert">{error}</p>}</>;
  }
  return <main className="staff-login">
    <section className="panel" aria-labelledby="sign-in-title">
      <p className="eyebrow">BANKER / STAFF ACCESS</p>
      <h1 id="sign-in-title">Sign in to your branch.</h1>
      <p>Fictional accounts. Teller and manager permissions are enforced by the server.</p>
      <form onSubmit={(e) => void login(e)}>
        <label htmlFor="staff-user">Staff username</label>
        <input id="staff-user" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required maxLength={40} />
        <label htmlFor="staff-password">Password</label>
        <input id="staff-password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && <p role="alert">{error}</p>}
        <button className="button primary" disabled={busy || !session}>{busy ? "Signing in…" : "Sign in"}</button>
        {!session && <button type="button" className="text-button" onClick={() => void load().catch((e: Error) => setError(e.message))}>Reconnect</button>}
      </form>
      <details><summary>Local demo credentials</summary><p>Teller: teller-1 or teller-2 / teller-demo-only<br />Manager: manager / manager-demo-only</p><p>Configured passwords may differ. Demo credentials are for local fictional data only.</p></details>
    </section>
  </main>;
}

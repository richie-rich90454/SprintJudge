import { useState } from "react";
import { useUIStore } from "../stores/useUIStore";
import { LogoMark } from "../components/LogoMark";

export function AdminLoginView() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const setView = useUIStore((s) => s.setView);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    try {
      // Fetch CSRF token cookie first
      await fetch("/admin/login", { credentials: "include" });
      // Read XSRF-TOKEN cookie
      const csrf = document.cookie
        .split("; ")
        .find((c) => c.startsWith("XSRF-TOKEN="))
        ?.split("=")[1] ?? "";
      const res = await fetch("/admin/login", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        credentials: "include",
        body: new URLSearchParams({
          username,
          password,
          _csrf: csrf ?? new URLSearchParams(window.location.search).get("_csrf") ?? "",
        }),
      });
      if (res.redirected || (res.url && !res.url.includes("error"))) {
        window.location.href = "/admin/dashboard";
      } else {
        setError("Invalid username or password");
      }
    } catch {
      setError("Connection error — is the server running?");
    }
  };

  return (
    <div className="pattern-exam min-h-screen flex items-center justify-center p-4">
      <form onSubmit={submit} className="card w-full max-w-sm">
        <div className="flex items-center gap-2.5 mb-6">
          <LogoMark size={28} />
          <span className="font-extrabold tracking-tight">SprintJudge Admin</span>
        </div>
        <label className="label-caps block mb-1" htmlFor="al-user">Username</label>
        <input id="al-user" value={username} onChange={(e) => setUsername(e.target.value)}
               className="input-underline mb-4" placeholder="admin" autoComplete="username" />
        <label className="label-caps block mb-1" htmlFor="al-pass">Password</label>
        <input id="al-pass" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
               className="input-underline mb-5" placeholder="••••••••" autoComplete="current-password" />
        {error && <p className="text-danger text-sm mb-3">{error}</p>}
        <button type="submit" className="btn btn-primary w-full">Sign in</button>
        <button type="button" onClick={() => setView("join")} className="btn btn-secondary btn-sm w-full mt-3">
          Back to player view
        </button>
      </form>
    </div>
  );
}

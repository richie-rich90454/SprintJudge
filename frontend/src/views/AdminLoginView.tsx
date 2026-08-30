import { useState } from "react";
import { Card, CardContent, Button } from "@heroui/react";
import { useUIStore } from "../stores/useUIStore";
import { LogoMark } from "../components/LogoMark";
import { ThemeToggle } from "../components/ThemeToggle";

export function AdminLoginView() {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const setView = useUIStore((s) => s.setView);

    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError("");
        try {
            const res = await fetch("/admin/login", {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                credentials: "include",
                body: new URLSearchParams({
                    username,
                    password,
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
            <Card className="bg-content1 w-full max-w-sm">
                <CardContent className="p-6">
                    <div className="flex items-center justify-between mb-6">
                        <div className="flex items-center gap-2.5">
                            <LogoMark size={28} />
                            <span className="font-extrabold tracking-tight">SprintJudge Admin</span>
                        </div>
                        <ThemeToggle />
                    </div>
                    <form onSubmit={submit} className="flex flex-col gap-4">
                        <label className="label-caps block mb-1" htmlFor="al-user">
                            Username
                        </label>
                        <input
                            id="al-user"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            placeholder="admin"
                            autoComplete="username"
                            className="input-underline"
                        />
                        <label className="label-caps block mb-1" htmlFor="al-pass">
                            Password
                        </label>
                        <input
                            id="al-pass"
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="password"
                            autoComplete="current-password"
                            className="input-underline"
                        />
                        {error && <p className="text-danger text-sm">{error}</p>}
                        <Button
                            type="submit"
                            variant="primary"
                            className="w-full bg-[var(--oq-red)] text-white"
                        >
                            Sign in
                        </Button>
                        <Button
                            variant="outline"
                            className="w-full"
                            onPress={() => setView("join")}
                        >
                            Back to player view
                        </Button>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}

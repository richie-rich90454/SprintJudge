import { Card, CardContent, Button } from "@heroui/react";
import { useUIStore } from "../stores/useUIStore";
import { LogoMark } from "../components/LogoMark";
import { ThemeToggle } from "../components/ThemeToggle";

export function AdminLoginView() {
    const setView = useUIStore((s) => s.setView);

    return (
        <div className="pattern-exam min-h-screen flex items-center justify-center p-4">
            <Card className="bg-[var(--oq-surface)] w-full max-w-sm">
                <CardContent className="p-6">
                    <div className="flex items-center justify-between mb-6">
                        <div className="flex items-center gap-2.5">
                            <LogoMark size={28} />
                            <span className="font-extrabold tracking-tight">SprintJudge Admin</span>
                        </div>
                        <ThemeToggle />
                    </div>
                    <form method="POST" action="/admin/login" className="flex flex-col gap-4">
                        <label className="label-caps block mb-1" htmlFor="al-user">
                            Username
                        </label>
                        <input
                            id="al-user"
                            name="username"
                            placeholder="admin"
                            autoComplete="username"
                            aria-required="true"
                            aria-invalid="false"
                            aria-describedby="al-error"
                            className="input-underline"
                        />
                        <label className="label-caps block mb-1" htmlFor="al-pass">
                            Password
                        </label>
                        <input
                            id="al-pass"
                            name="password"
                            type="password"
                            placeholder="password"
                            autoComplete="current-password"
                            aria-required="true"
                            aria-invalid="false"
                            aria-describedby="al-error"
                            className="input-underline"
                        />
                        <p
                            id="al-error"
                            role="alert"
                            className="hidden text-[var(--oq-danger)] text-sm"
                        />
                        <Button type="submit" className="btn btn-primary w-full">
                            Sign in
                        </Button>
                        <Button
                            className="btn btn-secondary w-full"
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
